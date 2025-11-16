package com.curihous.qbit.api.domain.stock.service;

import com.curihous.qbit.api.domain.stock.dto.response.StockRankingResponseDto;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.massive.dto.response.MassiveAggregateResponse;
import com.curihous.qbit.infra.massive.dto.response.MassiveSnapshotResponse;
import com.curihous.qbit.infra.massive.service.MassiveMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// JPA 영속성 컨텍스트를 건드리지 않고, Massive API만 호출하는 조정 용도라 여기에 위치시킴

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockRankingService {

    private final MassiveMarketService massiveMarketService;
    private final StockRepository stockRepository;
    
    private static final int MAX_PARALLEL_REQUESTS = 10;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS);

    // 상승률순 상위 종목 조회
    public List<StockRankingResponseDto> getTopGainers(User user, int limit) {
        try {
            List<Stock> candidateStocks = getCandidateStocks();

            List<CompletableFuture<Optional<StockRankingResponseDto>>> futures = candidateStocks.stream()
                    .map(stock -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return calculateChangePercentWithMassive(stock);
                        } catch (Exception e) {
                            log.debug("상승률 계산 실패: symbol={}, error={}", stock.getSymbol(), e.getMessage());
                            return Optional.<StockRankingResponseDto>empty();
                        }
                    }, executorService))
                    .collect(Collectors.toList());

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted((a, b) -> {
                        BigDecimal ca = a.changePercent() != null ? a.changePercent() : BigDecimal.ZERO;
                        BigDecimal cb = b.changePercent() != null ? b.changePercent() : BigDecimal.ZERO;
                        return cb.compareTo(ca);
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("상승률순 랭킹 조회 실패", e);
            return Collections.emptyList();
        }
    }

    // 거래량순 상위 종목 조회
    public List<StockRankingResponseDto> getTopVolumeSpikes(User user, int limit) {
        try {
            List<Stock> candidateStocks = getCandidateStocks();

            // 병렬로 각 종목의 거래량 급증 비율 계산
            List<CompletableFuture<Optional<VolumeSpikeResult>>> futures = candidateStocks.stream()
                    .map(stock -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return calculateVolumeSpikeWithMassive(stock);
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
            List<Stock> candidateStocks = getCandidateStocks();

            // 병렬로 각 종목의 변동성 계산
            List<CompletableFuture<Optional<VolatilityResult>>> futures = candidateStocks.stream()
                    .map(stock -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return calculateVolatilityWithMassive(stock);
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

    private List<Stock> getCandidateStocks() {
        // S&P500 플래그가 설정된 미국 주식만 랭킹 유니버스로 사용
        return stockRepository.findBySp500TrueAndAssetClassIgnoreCase("us_equity");
    }

    // Massive Snapshot 기반 상승률 계산
    private Optional<StockRankingResponseDto> calculateChangePercentWithMassive(Stock stock) {
        try {
            MassiveSnapshotResponse snapshot = massiveMarketService.getSnapshot(stock.getSymbol());
            if (snapshot == null || snapshot.getTicker() == null ||
                    snapshot.getTicker().getDay() == null || snapshot.getTicker().getPrevDay() == null) {
                return Optional.empty();
            }

            MassiveSnapshotResponse.DayData day = snapshot.getTicker().getDay();
            MassiveSnapshotResponse.PrevDayData prev = snapshot.getTicker().getPrevDay();
            if (day.getClose() == null || prev.getClose() == null || prev.getClose() == 0.0) {
                return Optional.empty();
            }

            BigDecimal currentPrice = BigDecimal.valueOf(day.getClose());
            BigDecimal prevClose = BigDecimal.valueOf(prev.getClose());
            BigDecimal changePercent = currentPrice.subtract(prevClose)
                    .divide(prevClose, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            StockRankingResponseDto dto = StockRankingResponseDto.builder()
                    .symbol(stock.getSymbol())
                    .stockName(stock.getStockName())
                    .currentPrice(currentPrice)
                    .changePercent(changePercent.setScale(4, RoundingMode.HALF_UP))
                    .build();

            return Optional.of(dto);
        } catch (Exception e) {
            log.debug("상승률 계산 중 오류: symbol={}, error={}", stock.getSymbol(), e.getMessage());
            return Optional.empty();
        }
    }

    // Massive Aggregates 기반 거래량 급증
    private Optional<VolumeSpikeResult> calculateVolumeSpikeWithMassive(Stock stock) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(30);
            MassiveAggregateResponse aggs = massiveMarketService.getAggregates(
                    stock.getSymbol(), 1, "day", from, to, true);

            if (aggs == null || aggs.getResults() == null || aggs.getResults().size() < 5) {
                return Optional.empty();
            }

            List<MassiveAggregateResponse.AggregateResult> bars = aggs.getResults();

            MassiveAggregateResponse.AggregateResult latestBar = bars.get(bars.size() - 1);
            Long currentVolume = latestBar.getVolume();
            if (currentVolume == null || currentVolume == 0) {
                return Optional.empty();
            }

            double avgVolume = bars.subList(0, bars.size() - 1).stream()
                    .mapToLong(bar -> bar.getVolume() != null ? bar.getVolume() : 0L)
                    .average()
                    .orElse(0.0);

            if (avgVolume == 0) {
                return Optional.empty();
            }

            BigDecimal spikeRatio = BigDecimal.valueOf(currentVolume)
                    .divide(BigDecimal.valueOf(avgVolume), 4, RoundingMode.HALF_UP);

            // 최신 가격 정보
            BigDecimal currentPrice = latestBar.getClosePrice() != null
                    ? BigDecimal.valueOf(latestBar.getClosePrice())
                    : BigDecimal.ZERO;

            // 최근 2개 종가로 등락률 계산 (없으면 0)
            BigDecimal changePercent = BigDecimal.ZERO;
            if (bars.size() >= 2) {
                MassiveAggregateResponse.AggregateResult prevBar = bars.get(bars.size() - 2);
                Double prevClose = prevBar.getClosePrice();
                Double lastClose = latestBar.getClosePrice();
                if (prevClose != null && prevClose != 0.0 && lastClose != null) {
                    BigDecimal last = BigDecimal.valueOf(lastClose);
                    BigDecimal prev = BigDecimal.valueOf(prevClose);
                    changePercent = last.subtract(prev)
                            .divide(prev, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }
            }

            StockRankingResponseDto dto = StockRankingResponseDto.builder()
                    .symbol(stock.getSymbol())
                    .stockName(stock.getStockName())
                    .currentPrice(currentPrice)
                    .changePercent(changePercent)
                    .build();

            return Optional.of(new VolumeSpikeResult(dto, spikeRatio));
        } catch (Exception e) {
            log.debug("거래량 급증 계산 중 오류(Massive): symbol={}, error={}", stock.getSymbol(), e.getMessage());
            return Optional.empty();
        }
    }

    // Massive Aggregates 기반 변동성
    private Optional<VolatilityResult> calculateVolatilityWithMassive(Stock stock) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(60);
            MassiveAggregateResponse aggs = massiveMarketService.getAggregates(
                    stock.getSymbol(), 1, "day", from, to, true);

            if (aggs == null || aggs.getResults() == null || aggs.getResults().size() < 10) {
                return Optional.empty();
            }

            List<MassiveAggregateResponse.AggregateResult> bars = aggs.getResults();

            List<Double> closes = bars.stream()
                    .map(MassiveAggregateResponse.AggregateResult::getClosePrice)
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
            
            // 최신 가격 정보 (마지막 종가 사용)
            Double lastClosePrice = closes.get(closes.size() - 1);
            BigDecimal currentPrice = BigDecimal.valueOf(lastClosePrice);

            // 최근 2개 종가로 등락률 계산 (없으면 0)
            BigDecimal changePercent = BigDecimal.ZERO;
            if (closes.size() >= 2) {
                double prevClose = closes.get(closes.size() - 2);
                double lastClose = closes.get(closes.size() - 1);
                if (prevClose != 0.0) {
                    BigDecimal last = BigDecimal.valueOf(lastClose);
                    BigDecimal prev = BigDecimal.valueOf(prevClose);
                    changePercent = last.subtract(prev)
                            .divide(prev, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }
            }

            StockRankingResponseDto dto = StockRankingResponseDto.builder()
                    .symbol(stock.getSymbol())
                    .stockName(stock.getStockName())
                    .currentPrice(currentPrice)
                    .changePercent(changePercent)
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

