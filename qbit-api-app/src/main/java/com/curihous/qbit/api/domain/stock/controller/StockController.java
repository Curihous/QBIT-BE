package com.curihous.qbit.api.domain.stock.controller;

import com.curihous.qbit.api.domain.stock.dto.response.StockDetailResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.StockRankingResponseDto;
import com.curihous.qbit.api.domain.stock.dto.response.StockSearchResponseDto;
import com.curihous.qbit.api.domain.stock.service.StockRankingService;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.service.StockService;
import com.curihous.qbit.infra.alpaca.service.AlpacaStockService;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "Stock", description = "주식 종목 관련 API입니다.(DB + Alpaca API)")
@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final AlpacaStockService alpacaStockService;
    private final StockRankingService stockRankingService;
    private final UserSecurityFacade userSecurityFacade;
    
    @Value("${stock.sync.us-equity}")
    private boolean allowUsEquity;
    
    @Value("${stock.sync.crypto}")
    private boolean allowCrypto;

    @Operation(
        summary = "주식 및 암호화폐 검색",
        description = "종목명(name) 또는 심볼(symbol)로 검색합니다. DB에 없는 심볼은 Alpaca API에서 자동 조회 후 저장됩니다. assetClass 파라미터로 필터링 가능합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<List<StockSearchResponseDto>> searchStocks(
            @Parameter(description = "검색어 (종목명 또는 심볼)", example = "Apple")
            @RequestParam(value = "q", required = false) String keyword,
            @Parameter(description = "자산 클래스 필터 (us_equity: 미국 주식, crypto: 암호화폐)", example = "us_equity")
            @RequestParam(value = "assetClass", required = false) String assetClass
    ) { // TODO: 페이징 처리
        // 1. DB에서 먼저 검색 (name 또는 symbol)
        List<Stock> stocks = stockService.searchStocks(keyword);
        
        // 2. DB에 없고, 검색어가 심볼 형식이면 Alpaca API 조회
        // 주식: AAPL, TSLA / 코인: BTC/USD, GRT/USDC
        if (keyword != null && !keyword.isBlank() && 
            stocks.isEmpty() && keyword.matches("^[A-Z0-9]{1,10}(/[A-Z]{3,4})?$")) {
            try {
                User user = userSecurityFacade.getCurrentUser();
                Stock stock = alpacaStockService.getOrFetchStock(user, keyword);
                
                // 허용된 자산 클래스만 검색 결과에 포함
                if ("us_equity".equalsIgnoreCase(stock.getAssetClass())) {
                    if (allowUsEquity) {
                        stocks = List.of(stock);
                    } else {
                        log.info("미국 주식 거래 비활성화됨: symbol={}", keyword);
                    }
                } else if ("crypto".equalsIgnoreCase(stock.getAssetClass())) {
                    if (allowCrypto) {
                        stocks = List.of(stock);
                    } else {
                        log.info("암호화폐 거래 비활성화됨: symbol={}", keyword);
                    }
                } else {
                    log.info("지원하지 않는 자산 클래스, 검색 결과 제외: symbol={}, class={}", 
                            keyword, stock.getAssetClass());
                }
            } catch (Exception e) {
                // Alpaca 조회 실패 시 빈 결과 반환 (프론트에서 예외 처리)
                log.debug("Alpaca API 조회 실패: {}", keyword);
            }
        }
        
        // 3. 허용되지 않은 자산 클래스 필터링
        stocks = stocks.stream()
                .filter(this::isAssetClassAllowed)
                .collect(Collectors.toList());
        
        // 4. assetClass 파라미터로 추가 필터링 (선택)
        if (assetClass != null && !assetClass.isBlank()) {
            stocks = stocks.stream()
                    .filter(stock -> assetClass.equalsIgnoreCase(stock.getAssetClass()))
                    .collect(Collectors.toList());
        }
        
        List<StockSearchResponseDto> response = stocks.stream()
                .map(StockSearchResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "특정 종목 상세 조회",
        description = "종목의 상세 정보를 조회합니다. DB에 없으면 Alpaca API에서 조회 후 자동 저장됩니다."
    )
    @GetMapping("/detail")
    public ResponseEntity<StockDetailResponseDto> getStock(
            @Parameter
            @RequestParam(value = "symbol") String symbol
    ) {
        User user = userSecurityFacade.getCurrentUser();
        // Cache-Aside: DB 우선 → 없으면 Alpaca API 조회 → 저장
        Stock stock = alpacaStockService.getOrFetchStock(user, symbol);
        
        if (!isAssetClassAllowed(stock)) {
            String assetClass = stock.getAssetClass();
            log.warn("허용되지 않은 자산 클래스 접근 시도: symbol={}, assetClass={}", symbol, assetClass);
            
            if ("crypto".equalsIgnoreCase(assetClass)) {
                throw new QbitException(ErrorCode.ASSET_CLASS_NOT_SUPPORTED, "암호화폐 거래는 현재 비활성화되어 있습니다.");
            } else if ("us_equity".equalsIgnoreCase(assetClass)) {
                throw new QbitException(ErrorCode.ASSET_CLASS_NOT_SUPPORTED, "미국 주식 거래는 현재 비활성화되어 있습니다.");
            } else {
                throw new QbitException(ErrorCode.ASSET_CLASS_NOT_SUPPORTED);
            }
        }
        
        return ResponseEntity.ok(StockDetailResponseDto.fromEntity(stock));
    }

    @Operation(
        summary = "상승률순 종목 랭킹 조회 (20개)",
        description = "당일 상승률 상위 종목을 조회합니다. (Alpaca movers API)"
    )
    @GetMapping("/ranking/moving")
    public ResponseEntity<List<StockRankingResponseDto>> getTopGainers() {
        User user = userSecurityFacade.getCurrentUser();
        List<StockRankingResponseDto> rankings = stockRankingService.getTopGainers(user, 20);
        return ResponseEntity.ok(rankings);
    }

    @Operation(
        summary = "거래량순 종목 랭킹 조회 (20개)",
        description = "거래량 급증 상위 종목을 조회합니다. (최근 1일 거래량 vs 과거 평균 거래량 비율을 계산)"
    )
    @GetMapping("/ranking/volume")
    public ResponseEntity<List<StockRankingResponseDto>> getTopVolumeSpikes() {
        User user = userSecurityFacade.getCurrentUser();
        List<StockRankingResponseDto> rankings = stockRankingService.getTopVolumeSpikes(user, 20);
        return ResponseEntity.ok(rankings);
    }

    @Operation(
        summary = "등락폭순 종목 랭킹 조회 (20개)",
        description = "변동성이 큰 상위 종목을 조회합니다. (최근 30일간 수익률의 표준편차를 계산)"
    )
    @GetMapping("/ranking/volatility")
    public ResponseEntity<List<StockRankingResponseDto>> getTopVolatility() {
        User user = userSecurityFacade.getCurrentUser();
        List<StockRankingResponseDto> rankings = stockRankingService.getTopVolatility(user, 20);
        return ResponseEntity.ok(rankings);
    }
    
    // 자산 클래스 허용 여부 체크 헬퍼 메서드
    private boolean isAssetClassAllowed(Stock stock) {
        String assetClass = stock.getAssetClass();
        
        if ("us_equity".equalsIgnoreCase(assetClass)) {
            return allowUsEquity;
        } else if ("crypto".equalsIgnoreCase(assetClass)) {
            return allowCrypto;
        }
        
        // 알 수 없는 자산 클래스는 기본적으로 허용하지 않음
        return false;
    }
}
