package com.curihous.qbit.infra.alpaca.service;

import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAssetResponse;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.port.StockPort;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Alpaca API를 통한 주식 데이터 조회 Adapter
 * (Hexagonal Architecture - Adapter)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlpacaStockService implements StockPort {

    private final StockRepository stockRepository;
    private final AlpacaTradingPort alpacaTradingPort;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;

    @Value("${stock.sync.on:false}")
    private boolean syncOnStartup;  // 환경변수로 강제 동기화 제어
    
    @Value("${stock.sync.us-equity}")
    private boolean syncUsEquity;  // 미국 주식 동기화 활성화 여부
    
    @Value("${stock.sync.crypto}")
    private boolean syncCrypto;  // TODO: 개발 후 false로 변경.  암호화폐 동기화 활성화 여부

    @PostConstruct
    public void initStocks() {
        try {
            long stockCount = stockRepository.count();
            
            // 1. 첫 배포
            if (stockCount < 100) {
                if (syncUsEquity) {
                    syncAllUSStocks();
                }
                if (syncCrypto) {
                    syncCryptoAssets();
                }
            } 
            // 1-2. 환경변수로 강제 동기화 설정된 경우 
            else if (syncOnStartup) {
                log.info("강제 동기화 설정 활성화. 현재 {}개 종목 → 전체 동기화 시작", stockCount);
                if (syncUsEquity) {
                    syncAllUSStocks();
                }
                if (syncCrypto) {
                    syncCryptoAssets();
                }
            } 
            else {
                log.info("DB 자산 동기화 건너뜀 (설정: us_equity={}, crypto={})", syncUsEquity, syncCrypto);
            }
        } catch (Exception e) {
            log.warn("초기 자산 동기화 실패 (서버는 정상 시작): {}", e.getMessage());
        }
    }

    // Cache-Aside 패턴: DB에 없으면 Alpaca API에서 조회 후 저장
    @Transactional
    public Stock getOrFetchStock(User user, String symbol) {
        // 1. DB에서 먼저 조회
        Optional<Stock> existing = stockRepository.findBySymbol(symbol);
        if (existing.isPresent()) {
            return existing.get();  // 캐시 히트
        }

        // 2. 캐시 미스 → Alpaca API에서 조회
        log.info("DB에 종목 없음. Alpaca API로 조회: symbol={}", symbol);
        
        // 시스템 계정 사용
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(1L);
        String authorization = "Bearer " + connection.getAccessToken();

        try {
            AlpacaAssetResponse alpacaAsset = alpacaTradingPort.getAsset(authorization, symbol);
            
            // 3. DB에 저장 (다음부터는 캐시 히트)
            Stock savedStock = createStock(alpacaAsset);
            log.info("Alpaca API로 조회한 종목 DB 저장 완료: symbol={}", symbol);
            
            return savedStock;
        } catch (Exception e) {
            log.error("Alpaca 종목 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "종목을 찾을 수 없습니다: " + symbol);
        }
    }

    // 신규 종목 저장 (DB에 없다는 것이 확인된 경우)
    @Transactional
    public Stock createStock(AlpacaAssetResponse assetResponse) {
        // 회사명에서 도메인 자동 생성
        String generatedDomain = Stock.generateDomainFromName(assetResponse.name());
        
        Stock stock = Stock.builder()
                .symbol(assetResponse.symbol())
                .stockName(assetResponse.name())
                .exchange(assetResponse.exchange())
                .assetClass(assetResponse.assetClass())
                .status(assetResponse.status())
                .tradable(assetResponse.tradable())
                .fractionable(assetResponse.fractionable())
                .minOrderSize(assetResponse.minOrderSize())
                .minTradeIncrement(assetResponse.minTradeIncrement())
                .priceIncrement(assetResponse.priceIncrement())
                .companyDomain(generatedDomain) // name 필드에서 자동 생성
                .build();
        
        log.debug("종목 도메인 자동 생성: {} → {}", assetResponse.name(), generatedDomain);
        
        return stockRepository.save(stock);
    }

    // ========= 배치 작업 =========
    
    // 미국 주식 전체 동기화 (매월 1일 7시)
    @Scheduled(cron = "0 0 7 1 * ?", zone = "Asia/Seoul")
    @Transactional
    public void syncAllUSStocks() {
        if (!syncUsEquity) {
            log.debug("미국 주식 동기화 비활성화됨 (stock.sync.us-equity=false)");
            return;
        }
        
        log.info("미국 주식 동기화 시작 (NYSE + NASDAQ)");
        
        try {
            // 시스템 계정으로 Alpaca API 호출
            // TODO: 시스템 계정 DB에 추가(@PostConstruct)
            AlpacaOAuthConnection systemConnection = alpacaOAuthConnectionService.getValidConnection(1L);
            String authorization = "Bearer " + systemConnection.getAccessToken();
            
            // Alpaca API에서 모든 미국 주식 조회 (NYSE + NASDAQ)
            List<AlpacaAssetResponse> allStocks = alpacaTradingPort.getAssets(
                    authorization, "active", "us_equity"
            );
            
            log.info("Alpaca API로부터 {}개 종목 조회 완료", allStocks.size());
            
            int newCount = 0;
            int updateCount = 0;
            int failCount = 0;
            
            for (AlpacaAssetResponse asset : allStocks) {
                try {
                    // 거래 가능한 종목만 저장
                    if (!Boolean.TRUE.equals(asset.tradable())) {
                        continue;
                    }
                    
                    // DB에 있으면 업데이트, 없으면 신규 저장
                    Optional<Stock> existing = stockRepository.findBySymbol(asset.symbol());
                    if (existing.isEmpty()) {
                        createStock(asset);
                        newCount++;
                    } else {
                        Stock stock = existing.get();
                        stock.updateFromAlpaca(
                                asset.name(),
                                asset.exchange(),
                                asset.assetClass(),
                                asset.status(),
                                asset.tradable(),
                                asset.fractionable(),
                                asset.minOrderSize(),
                                asset.minTradeIncrement(),
                                asset.priceIncrement()
                        );
                        
                        // 도메인이 없는 경우에만 자동 생성 (수동 설정한 도메인 보호)
                        if (stock.getCompanyDomain() == null || stock.getCompanyDomain().isBlank()) {
                            String generatedDomain = Stock.generateDomainFromName(asset.name());
                            stock.setCompanyDomain(generatedDomain);
                        }
                        
                        stockRepository.save(stock);
                        updateCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.warn("종목 동기화 실패: symbol={}, error={}", asset.symbol(), e.getMessage());
                }
            }
            
            log.info("미국 주식 동기화 완료: 신규={}, 기존={}, 실패={}, 전체={}", 
                    newCount, updateCount, failCount, allStocks.size());
            
        } catch (Exception e) {
            log.error("미국 주식 동기화 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "주식 동기화에 실패했습니다: " + e.getMessage());
        }
    }

    // 암호화폐 자산 동기화 (매일 7시)
    @Scheduled(cron = "0 0 7 * * ?", zone = "Asia/Seoul")
    @Transactional
    public void syncCryptoAssets() {
        if (!syncCrypto) {
            log.debug("암호화폐 동기화 비활성화됨 (stock.sync.crypto=false)");
            return;
        }
        
        log.info("암호화폐 자산 동기화 시작 (BTC, ETH 등)");
        
        try {
            // 시스템 계정으로 Alpaca API 호출
            AlpacaOAuthConnection systemConnection = alpacaOAuthConnectionService.getValidConnection(1L);
            String authorization = "Bearer " + systemConnection.getAccessToken();
            
            // Alpaca API에서 모든 암호화폐 조회
            List<AlpacaAssetResponse> cryptoAssets = alpacaTradingPort.getAssets(
                    authorization, "active", "crypto"
            );
            
            log.info("Alpaca API로부터 {}개 암호화폐 조회 완료", cryptoAssets.size());
            
            int newCount = 0;
            int updateCount = 0;
            int failCount = 0;
            
            for (AlpacaAssetResponse asset : cryptoAssets) {
                try {
                    // 거래 가능한 자산만 저장
                    if (!Boolean.TRUE.equals(asset.tradable())) {
                        continue;
                    }
                    
                    // DB에 있으면 업데이트, 없으면 신규 저장
                    Optional<Stock> existing = stockRepository.findBySymbol(asset.symbol());
                    if (existing.isEmpty()) {
                        createStock(asset);
                        newCount++;
                    } else {
                        Stock stock = existing.get();
                        stock.updateFromAlpaca(
                                asset.name(),
                                asset.exchange(),
                                asset.assetClass(),
                                asset.status(),
                                asset.tradable(),
                                asset.fractionable(),
                                asset.minOrderSize(),
                                asset.minTradeIncrement(),
                                asset.priceIncrement()
                        );
                        
                        // 암호화폐는 회사 도메인이 없으므로 null로 유지
                        stockRepository.save(stock);
                        updateCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.warn("암호화폐 동기화 실패: symbol={}, error={}", asset.symbol(), e.getMessage());
                }
            }
            
            log.info("암호화폐 동기화 완료: 신규={}, 기존={}, 실패={}, 전체={}", 
                    newCount, updateCount, failCount, cryptoAssets.size());
            
        } catch (Exception e) {
            log.error("암호화폐 동기화 실패: {}", e.getMessage(), e);
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "암호화폐 동기화에 실패했습니다: " + e.getMessage());
        }
    }

}
