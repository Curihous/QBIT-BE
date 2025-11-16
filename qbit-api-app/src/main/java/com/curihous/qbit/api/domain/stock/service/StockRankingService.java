package com.curihous.qbit.api.domain.stock.service;

import com.curihous.qbit.api.domain.stock.dto.response.StockRankingResponseDto;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.alpaca.client.AlpacaDataClient;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaBarsResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaMoversResponse;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockRankingService {

    private final AlpacaDataClient alpacaDataClient;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final StockRepository stockRepository;
    
    private static final int MAX_PARALLEL_REQUESTS = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS);

    // 상승률순 상위 종목 조회
    public List<StockRankingResponseDto> getTopGainers(User user, int limit) {
        try {
            String authorization = getAuthorization(user);
            AlpacaMoversResponse moversResponse = alpacaDataClient.getMovers(authorization);
            
            if (moversResponse == null || moversResponse.getGainers() == null) {
                log.warn("Alpaca movers API 응답이 비어있습니다.");
                return Collections.emptyList();
            }
            
            // change_percent 기준 내림차순 정렬 후 상위 limit개 선택
            List<AlpacaMoversResponse.MoverItem> topGainers = moversResponse.getGainers().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getChangePercent() != null ? b.getChangePercent() : 0.0,
                            a.getChangePercent() != null ? a.getChangePercent() : 0.0
                    ))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            return convertMoversToDto(topGainers);
        } catch (Exception e) {
            log.error("상승률순 랭킹 조회 실패", e);
            return Collections.emptyList();
        }
    }

    // 거래량순 상위 종목 조회
    public List<StockRankingResponseDto> getTopVolumeSpikes(User user, int limit) {
        try {
            // S&P500 종목 리스트 가져오기
            List<Stock> candidateStocks = getCandidateStocks();
            
            String authorization = getAuthorization(user);
            
            // 병렬로 각 종목의 거래량 급증 비율 계산
            List<CompletableFuture<Optional<VolumeSpikeResult>>> futures = candidateStocks.stream()
                    .map(stock -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return calculateVolumeSpike(authorization, stock);
                        } catch (Exception e) {
                            log.debug("거래량 급증 계산 실패: symbol={}, error={}", stock.getSymbol(), e.getMessage());
                            return Optional.<VolumeSpikeResult>empty();
                        }
                    }, executorService))
                    .collect(Collectors.toList());
            
            List<StockRankingResponseDto> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted((a, b) -> b.spikeRatio().compareTo(a.spikeRatio()))
                    .map(VolumeSpikeResult::dto)
                    .limit(limit)
                    .collect(Collectors.toList());
            
            return results;
        } catch (Exception e) {
            log.error("거래량순 랭킹 조회 실패", e);
            return Collections.emptyList();
        }
    }

    // 등락폭순 상위 종목 조회
    public List<StockRankingResponseDto> getTopVolatility(User user, int limit) {
        try {
            // S&P500 종목 리스트 가져오기
            List<Stock> candidateStocks = getCandidateStocks();
            
            String authorization = getAuthorization(user);
            
            // 병렬로 각 종목의 변동성 계산
            List<CompletableFuture<Optional<VolatilityResult>>> futures = candidateStocks.stream()
                    .map(stock -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return calculateVolatility(authorization, stock);
                        } catch (Exception e) {
                            log.debug("변동성 계산 실패: symbol={}, error={}", stock.getSymbol(), e.getMessage());
                            return Optional.<VolatilityResult>empty();
                        }
                    }, executorService))
                    .collect(Collectors.toList());
            
            List<StockRankingResponseDto> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted((a, b) -> b.volatility().compareTo(a.volatility()))
                    .map(VolatilityResult::dto)
                    .limit(limit)
                    .collect(Collectors.toList());
            
            return results;
        } catch (Exception e) {
            log.error("등락폭순 랭킹 조회 실패", e);
            return Collections.emptyList();
        }
    }

    // ============== 헬퍼 메서드 ==============

    private String getAuthorization(User user) {
        // 시스템 계정 사용 (userId=1L)
        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(1L);
        return "Bearer " + connection.getAccessToken();
    }

    private List<Stock> getCandidateStocks() {
        // S&P500 플래그가 설정된 미국 주식만 랭킹 유니버스로 사용
        return stockRepository.findBySp500TrueAndAssetClassIgnoreCase("us_equity");
    }

    private List<StockRankingResponseDto> convertMoversToDto(List<AlpacaMoversResponse.MoverItem> movers) {
        return movers.stream()
                .map(mover -> {
                    Stock stock = stockRepository.findBySymbol(mover.getSymbol()).orElse(null);
                    return StockRankingResponseDto.builder()
                            .symbol(mover.getSymbol())
                            .stockName(stock != null ? stock.getStockName() : mover.getSymbol())
                            .currentPrice(mover.getPrice() != null ? BigDecimal.valueOf(mover.getPrice()) : null)
                            .changePercent(mover.getChangePercent() != null ? BigDecimal.valueOf(mover.getChangePercent()) : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Optional<VolumeSpikeResult> calculateVolumeSpike(String authorization, Stock stock) {
        try {
            // 최근 10일간 bars 조회
            AlpacaBarsResponse barsResponse = alpacaDataClient.getBars(
                    authorization,
                    stock.getSymbol(),
                    "1Day",
                    null,
                    null,
                    10
            );
            
            if (barsResponse == null || barsResponse.getBars() == null || barsResponse.getBars().size() < 5) {
                return Optional.empty();
            }
            
            List<AlpacaBarsResponse.Bar> bars = barsResponse.getBars();
            
            // 최신 거래량
            Long currentVolume = bars.get(bars.size() - 1).getVolume();
            if (currentVolume == null || currentVolume == 0) {
                return Optional.empty();
            }
            
            // 과거 평균 거래량 (최신 제외)
            double avgVolume = bars.subList(0, bars.size() - 1).stream()
                    .mapToLong(bar -> bar.getVolume() != null ? bar.getVolume() : 0L)
                    .average()
                    .orElse(0.0);
            
            if (avgVolume == 0) {
                return Optional.empty();
            }
            
            // 거래량 급증 비율
            BigDecimal spikeRatio = BigDecimal.valueOf(currentVolume)
                    .divide(BigDecimal.valueOf(avgVolume), 4, RoundingMode.HALF_UP);
            
            // 최신 가격 정보
            AlpacaBarsResponse.Bar latestBar = bars.get(bars.size() - 1);
            BigDecimal currentPrice = latestBar.getClose() != null 
                    ? BigDecimal.valueOf(latestBar.getClose()) 
                    : null;
            
            StockRankingResponseDto dto = StockRankingResponseDto.builder()
                    .symbol(stock.getSymbol())
                    .stockName(stock.getStockName())
                    .currentPrice(currentPrice)
                    .changePercent(null)
                    .build();

            return Optional.of(new VolumeSpikeResult(dto, spikeRatio));
        } catch (Exception e) {
            log.debug("거래량 급증 계산 중 오류: symbol={}, error={}", stock.getSymbol(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<VolatilityResult> calculateVolatility(String authorization, Stock stock) {
        try {
            // 최근 30일간 bars 조회
            AlpacaBarsResponse barsResponse = alpacaDataClient.getBars(
                    authorization,
                    stock.getSymbol(),
                    "1Day",
                    null,
                    null,
                    30
            );
            
            if (barsResponse == null || barsResponse.getBars() == null || barsResponse.getBars().size() < 10) {
                return Optional.empty();
            }
            
            List<AlpacaBarsResponse.Bar> bars = barsResponse.getBars();
            
            // 종가 리스트
            List<Double> closes = bars.stream()
                    .map(AlpacaBarsResponse.Bar::getClose)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (closes.size() < 10) {
                return Optional.empty();
            }
            
            // 일간 수익률 계산
            List<Double> returns = new ArrayList<>();
            for (int i = 1; i < closes.size(); i++) {
                double prevClose = closes.get(i - 1);
                double currentClose = closes.get(i);
                if (prevClose != 0) {
                    returns.add((currentClose - prevClose) / prevClose);
                }
            }
            
            if (returns.isEmpty()) {
                return Optional.empty();
            }
            
            // 표준편차 계산 (변동성)
            double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = returns.stream()
                    .mapToDouble(r -> Math.pow(r - mean, 2))
                    .average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);
            
            // 최신 가격 정보
            AlpacaBarsResponse.Bar latestBar = bars.get(bars.size() - 1);
            BigDecimal currentPrice = latestBar.getClose() != null 
                    ? BigDecimal.valueOf(latestBar.getClose()) 
                    : null;
            
            StockRankingResponseDto dto = StockRankingResponseDto.builder()
                    .symbol(stock.getSymbol())
                    .stockName(stock.getStockName())
                    .currentPrice(currentPrice)
                    .changePercent(null)
                    .build();

            return Optional.of(new VolatilityResult(dto, BigDecimal.valueOf(stdDev).setScale(6, RoundingMode.HALF_UP)));
        } catch (Exception e) {
            log.debug("변동성 계산 중 오류: symbol={}, error={}", stock.getSymbol(), e.getMessage());
            return Optional.empty();
        }
    }

    // 내부용 정렬 메타데이터
    private record VolumeSpikeResult(StockRankingResponseDto dto, BigDecimal spikeRatio) {}
    private record VolatilityResult(StockRankingResponseDto dto, BigDecimal volatility) {}
}

