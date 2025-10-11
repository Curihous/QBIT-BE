package com.curihous.qbit.infra.yahoo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yahoo Finance API 차트 응답 DTO
 * 
 * 사용 API:
 * - Yahoo Finance: GET /v8/finance/chart/{symbol}
 * - QBIT API: GET /indices
 * - QBIT API: GET /indices/{symbol}
 * - QBIT API: GET /indices/{symbol}/history
 * 
 * Record가 아닌 Class를 사용한 이유: 중첩 클래스의 역직렬화가 더 쉽게 가능
 */
@Data
public class YahooFinanceChartResponse {

    @JsonProperty("chart")
    private ChartData chart;

    @Data
    public static class ChartData {
        @JsonProperty("result")
        private List<Result> result;

        @JsonProperty("error")
        private Object error;
    }

    @Data
    public static class Result {
        @JsonProperty("meta")
        private Meta meta;

        @JsonProperty("timestamp")
        private List<Long> timestamp;

        @JsonProperty("indicators")
        private Indicators indicators;
    }

    @Data
    public static class Meta {
        @JsonProperty("currency")
        private String currency;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("exchangeName")
        private String exchangeName;

        @JsonProperty("instrumentType")
        private String instrumentType;

        @JsonProperty("regularMarketPrice")
        private BigDecimal regularMarketPrice;

        @JsonProperty("previousClose")
        private BigDecimal previousClose;

        @JsonProperty("regularMarketTime")
        private Long regularMarketTime;

        @JsonProperty("regularMarketVolume")
        private Long regularMarketVolume;
    }

    @Data
    public static class Indicators {
        @JsonProperty("quote")
        private List<Quote> quote;
    }

    @Data
    public static class Quote {
        @JsonProperty("open")
        private List<BigDecimal> open;

        @JsonProperty("high")
        private List<BigDecimal> high;

        @JsonProperty("low")
        private List<BigDecimal> low;

        @JsonProperty("close")
        private List<BigDecimal> close;

        @JsonProperty("volume")
        private List<Long> volume;
    }

    // 현재 가격 반환 
    public BigDecimal getCurrentPrice() {
        if (chart != null && chart.result != null && !chart.result.isEmpty()) {
            Meta meta = chart.result.get(0).getMeta();
            return meta != null && meta.getRegularMarketPrice() != null 
                ? meta.getRegularMarketPrice() 
                : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    // 전일 종가 반환
    public BigDecimal getPreviousClose() {
        if (chart != null && chart.result != null && !chart.result.isEmpty()) {
            Meta meta = chart.result.get(0).getMeta();
            return meta != null && meta.getPreviousClose() != null 
                ? meta.getPreviousClose() 
                : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    // 변동 금액 반환
    public BigDecimal getChangeAmount() {
        return getCurrentPrice().subtract(getPreviousClose());
    }

    // 변동률 반환
    public BigDecimal getChangePercentage() {
        BigDecimal previous = getPreviousClose();
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getChangeAmount().divide(previous, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    // 거래량 반환
    public Long getVolume() {
        if (chart != null && chart.result != null && !chart.result.isEmpty()) {
            Meta meta = chart.result.get(0).getMeta();
            return meta != null ? meta.getRegularMarketVolume() : 0L;
        }
        return 0L;
    }

    // 심볼 반환
    public String getSymbol() {
        if (chart != null && chart.result != null && !chart.result.isEmpty()) {
            Meta meta = chart.result.get(0).getMeta();
            return meta != null ? meta.getSymbol() : null;
        }
        return null;
    }
}

