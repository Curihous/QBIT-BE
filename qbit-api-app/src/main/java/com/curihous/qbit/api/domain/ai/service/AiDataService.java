package com.curihous.qbit.api.domain.ai.service;

import com.curihous.qbit.api.domain.ai.dto.response.ReportTradeCycleResponseDto;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.api.domain.stock.dto.response.CandleResponseDto;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.service.TradeCycleService;
import com.curihous.qbit.infra.binance.service.BinanceMarketService;
import com.curihous.qbit.infra.massive.service.MassiveMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDataService {
    
    private final TradeCycleService tradeCycleService;
    private final OrderRequestRepository orderRequestRepository;
    private final BinanceMarketService binanceMarketService;
    private final MassiveMarketService massiveMarketService;
    
    // AI 서버용 특정 TradeCycle 데이터 조회
    public ReportTradeCycleResponseDto getTradeCycleForAi(Long tradeCycleId) {
        TradeCycle tradeCycle = tradeCycleService.getTradeCycleById(tradeCycleId);
        ensureTradeCycleHasPeriod(tradeCycle);
        String interval = determineInterval(tradeCycle);
        // TODO: 보안을 위해 IP 화이트리스트 또는 API 키 기반 인증 추가 고려
        ReportTradeCycleResponseDto result = convertToAiDto(tradeCycle, interval);
        return result;
    }
    
    private ReportTradeCycleResponseDto convertToAiDto(TradeCycle tradeCycle, String interval) {
        String symbol = tradeCycle.getStock().getSymbol();

        // 차트 데이터 조회
        List<ReportTradeCycleResponseDto.CandleData> chartData = fetchChartData(tradeCycle, interval);
        
        // 매수/매도 지점 조회 (OrderRequest의 FILLED 상태)
        List<ReportTradeCycleResponseDto.TradePoint> tradePoints = fetchTradePoints(tradeCycle.getId());
        return ReportTradeCycleResponseDto.builder()
            .tradeCycleId(tradeCycle.getId())
            .symbol(symbol)
            .startDate(tradeCycle.getStartDate())
            .endDate(tradeCycle.getEndDate())
            .interval(interval)
            .profitLossRate(tradeCycle.getProfitLossRate())
            .averageBuyPrice(tradeCycle.getAverageBuyPrice())
            .averageSellPrice(tradeCycle.getAverageSellPrice())
            .totalInvestmentAmount(tradeCycle.getTotalInvestmentAmount())
            .chartData(chartData)
            .tradePoints(tradePoints)
            .build();
    }

    // ========== 헬퍼 메서드 ==========

    private void ensureTradeCycleHasPeriod(TradeCycle tradeCycle) {
        if (tradeCycle.getStartDate() == null || tradeCycle.getEndDate() == null) {
            throw new QbitException(
                ErrorCode.INVALID_TRADE_CYCLE,
                "TradeCycle 기간 정보가 없어 데이터를 조회할 수 없습니다."
            );
        }
    }

    private String determineInterval(TradeCycle tradeCycle) {
        LocalDateTime start = tradeCycle.getStartDate();
        LocalDateTime end = tradeCycle.getEndDate();
        long durationMinutes = Math.max(Duration.between(start, end).abs().toMinutes(), 1);

        if (durationMinutes <= 6 * 60) { // 6시간 이하
            return "5m";
        }
        if (durationMinutes <= 24 * 60) { // 1일 이하
            return "15m";
        }
        if (durationMinutes <= 3 * 24 * 60) { // 3일 이하
            return "30m";
        }
        if (durationMinutes <= 7 * 24 * 60) { // 1주 이하
            return "1h";
        }
        if (durationMinutes <= 30 * 24 * 60) { // 1달 이하
            return "4h";
        }
        if (durationMinutes <= 90 * 24 * 60) { // 3달 이하
            return "12h";
        }
        if (durationMinutes <= 180 * 24 * 60) { // 6달 이하
            return "1d";
        }
        return "1w";
    }

    private List<ReportTradeCycleResponseDto.CandleData> fetchChartData(TradeCycle tradeCycle, String interval) {
        Stock stock = tradeCycle.getStock();
        if (stock == null) {
            return Collections.emptyList();
        }

        LocalDateTime startDate = tradeCycle.getStartDate();
        LocalDateTime endDate = tradeCycle.getEndDate();
        // 과거 캔들 데이터를 제공해야 매수 시점에 지표 계산이 가능하므로
        LocalDateTime extendedStartDate = calculateExtendedStartDate(startDate, interval); 

        String assetClass = stock.getAssetClass();
        if ("crypto".equalsIgnoreCase(assetClass)) {
            return fetchCryptoCandles(stock.getBinanceSymbol(), interval, extendedStartDate, endDate);
        }
        if ("us_equity".equalsIgnoreCase(assetClass)) {
            return fetchUsEquityCandles(stock.getSymbol(), interval, extendedStartDate, endDate);
        }

        log.debug("지원하지 않는 자산 클래스이므로 차트 데이터를 제공하지 않습니다. assetClass={}, symbol={}", assetClass, stock.getSymbol());
        return Collections.emptyList();
    }
    
    // 충분한 캔들 데이터 제공을 위해 과거 기간 계산(300개 기준)
    private LocalDateTime calculateExtendedStartDate(LocalDateTime startDate, String interval) {
        Duration historyDuration = switch (interval) {
            case "5m" -> Duration.ofHours(26); // 300개 * 5분 = 25시간, 안전하게 26시간
            case "15m" -> Duration.ofDays(4); // 300개 * 15분 = 75시간, 안전하게 4일
            case "30m" -> Duration.ofDays(7); // 300개 * 30분 = 150시간, 안전하게 7일
            case "1h" -> Duration.ofDays(13); // 300개 * 1시간 = 12.5일, 안전하게 13일
            case "4h" -> Duration.ofDays(50); // 300개 * 4시간 = 50일
            case "12h" -> Duration.ofDays(150); // 300개 * 12시간 = 150일
            case "1d" -> Duration.ofDays(300); // 300개 * 1일 = 300일
            case "1w" -> Duration.ofDays(2100); // 300개 * 1주 = 약 2100일
            default -> Duration.ofDays(13); // 기본값
        };
        
        return startDate.minus(historyDuration);
    }

    private List<ReportTradeCycleResponseDto.CandleData> fetchCryptoCandles(
        String binanceSymbol,
        String interval,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        if (binanceSymbol == null || binanceSymbol.isBlank()) {
            log.debug("Binance 심볼이 없어 차트 데이터를 조회하지 않습니다.");
            return Collections.emptyList();
        }

        try {
            Long startTime = startDate != null
                ? startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null;
            Long endTime = endDate != null
                ? endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null;

            List<List<String>> klines = binanceMarketService.getKlines(
                binanceSymbol,
                interval,
                startTime,
                endTime,
                1000 // 최대 1000개 캔들 데이터 제한
            );

            CandleResponseDto candleResponse = CandleResponseDto.fromBinance(binanceSymbol, interval, klines);
            return mapCandleResponseToAi(candleResponse);
        } catch (Exception e) {
            log.warn("Binance 차트 데이터 조회 실패: symbol={}, error={}", binanceSymbol, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<ReportTradeCycleResponseDto.CandleData> fetchUsEquityCandles(
        String ticker,
        String interval,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        if (ticker == null || ticker.isBlank()) {
            log.debug("티커가 없어 미국 주식 차트 데이터를 조회하지 않습니다.");
            return Collections.emptyList();
        }

        IntervalSpec intervalSpec = resolveUsEquityInterval(interval);
        LocalDate from = startDate != null ? startDate.toLocalDate() : LocalDate.now().minusDays(30);
        LocalDate to = endDate != null ? endDate.toLocalDate() : LocalDate.now();

        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        try {
            var aggregates = massiveMarketService.getAggregates(
                ticker,
                intervalSpec.multiplier(),
                intervalSpec.timespan(),
                from,
                to,
                true
            );

            CandleResponseDto candleResponse = CandleResponseDto.fromMassive(
                ticker,
                intervalSpec.multiplier(),
                intervalSpec.timespan(),
                aggregates
            );
            return mapCandleResponseToAi(candleResponse);
        } catch (Exception e) {
            log.warn("미국 주식 차트 데이터 조회 실패: ticker={}, error={}", ticker, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<ReportTradeCycleResponseDto.CandleData> mapCandleResponseToAi(CandleResponseDto candleResponse) {
        if (candleResponse == null || candleResponse.candles() == null) {
            return Collections.emptyList();
        }
        return candleResponse.candles().stream()
            .map(candle -> ReportTradeCycleResponseDto.CandleData.builder()
                .timestamp(candle.timestamp())
                .open(formatDouble(candle.open()))
                .high(formatDouble(candle.high()))
                .low(formatDouble(candle.low()))
                .close(formatDouble(candle.close()))
                .volume(formatDouble(candle.volume()))
                .build())
            .collect(Collectors.toList());
    }

    private String formatDouble(Double value) {
        return value != null ? value.toString() : null;
    }

    private IntervalSpec resolveUsEquityInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return new IntervalSpec(1, "day");
        }

        String trimmed = interval.trim();
        char suffixChar = trimmed.charAt(trimmed.length() - 1);
        String numericPart = trimmed.substring(0, trimmed.length() - 1);

        int multiplier;
        try {
            multiplier = Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            multiplier = 1;
        }

        char lowerSuffix = Character.toLowerCase(suffixChar);
        return switch (lowerSuffix) {
            case 'm' -> suffixChar == 'M'
                ? new IntervalSpec(multiplier, "month")
                : new IntervalSpec(multiplier, "minute");
            case 'h' -> new IntervalSpec(multiplier, "hour");
            case 'w' -> new IntervalSpec(multiplier, "week");
            case 'd' -> new IntervalSpec(multiplier, "day");
            case 'y' -> new IntervalSpec(multiplier, "year");
            default -> new IntervalSpec(multiplier, "day");
        };
    }

    private record IntervalSpec(int multiplier, String timespan) {}
    
    // OrderRequest의 FILLED 상태에서 매수/매도 지점 조회 
    private List<ReportTradeCycleResponseDto.TradePoint> fetchTradePoints(Long tradeCycleId) {
        // TradeCycle에 연결된 체결된 주문 조회 
        List<OrderRequest> filledOrders = orderRequestRepository.findByTradeCycleIdAndStatusIn(
            tradeCycleId,
            List.of(OrderStatus.FILLED, OrderStatus.PARTIALLY_FILLED)
        );
        
        return filledOrders.stream()
            .filter(order -> order.getFilledAt() != null) // filledAt이 있는 것만
            .map(this::toTradePoint)
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    }

    private Optional<ReportTradeCycleResponseDto.TradePoint> toTradePoint(OrderRequest order) {
        BigDecimal filledPrice = order.getFilledAvgPrice();
        BigDecimal filledQuantity = order.getFilledQuantity();

        if (filledPrice == null || filledQuantity == null || filledQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("TradePoint 생성을 건너뜀 - 체결 정보 미완: orderId={}, status={}, filledAvgPrice={}, filledQuantity={}",
                order.getId(), order.getStatus(), filledPrice, filledQuantity);
            return Optional.empty();
        }

        return Optional.of(
            ReportTradeCycleResponseDto.TradePoint.builder()
                .timestamp(order.getFilledAt().toInstant().toEpochMilli())
                .side(order.getSide().name())
                .price(filledPrice)
                .quantity(filledQuantity)
                .build()
        );
    }
}

