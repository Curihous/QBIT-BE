package com.curihous.qbit.api.domain.ai.service;

import com.curihous.qbit.api.domain.ai.dto.response.ReportTradeCycleResponseDto;
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
    public ReportTradeCycleResponseDto getTradeCycleForAi(Long tradeCycleId, String interval) {
        log.info("AI용 단일 TradeCycle 데이터 조회 시작: tradeCycleId={}, interval={}",
                tradeCycleId, interval);
        
        TradeCycle tradeCycle = tradeCycleService.getTradeCycleById(tradeCycleId);
        
        // TODO: 보안을 위해 IP 화이트리스트 또는 API 키 기반 인증 추가 고려
        
        ReportTradeCycleResponseDto result = convertToAiDto(tradeCycle, interval);
        
        log.info("AI용 단일 TradeCycle 데이터 조회 완료: tradeCycleId={}", tradeCycleId);
        
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
            .profitLossRate(tradeCycle.getProfitLossRate())
            .averageBuyPrice(tradeCycle.getAverageBuyPrice())
            .averageSellPrice(tradeCycle.getAverageSellPrice())
            .totalInvestmentAmount(tradeCycle.getTotalInvestmentAmount())
            .chartData(chartData)
            .tradePoints(tradePoints)
            .build();
    }

    private List<ReportTradeCycleResponseDto.CandleData> fetchChartData(TradeCycle tradeCycle, String interval) {
        Stock stock = tradeCycle.getStock();
        if (stock == null) {
            return Collections.emptyList();
        }

        String assetClass = stock.getAssetClass();
        if ("crypto".equalsIgnoreCase(assetClass)) {
            return fetchCryptoCandles(stock.getBinanceSymbol(), interval, tradeCycle.getStartDate(), tradeCycle.getEndDate());
        }
        if ("us_equity".equalsIgnoreCase(assetClass)) {
            return fetchUsEquityCandles(stock.getSymbol(), interval, tradeCycle.getStartDate(), tradeCycle.getEndDate());
        }

        log.debug("지원하지 않는 자산 클래스이므로 차트 데이터를 제공하지 않습니다. assetClass={}, symbol={}", assetClass, stock.getSymbol());
        return Collections.emptyList();
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
                500
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

