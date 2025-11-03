package com.curihous.qbit.infra.massive.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Massive Snapshot 응답 DTO
 * 
 * 사용 API: GET /v2/snapshot/locale/us/markets/stocks/tickers/{ticker}
 * $29 플랜에서 시세 탭 구현용 (현재가, 고가, 저가, 시가, 전일 종가 포함)
 */
@Data
public class MassiveSnapshotResponse {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("ticker")
    private TickerData ticker;
    
    @Data
    public static class TickerData {
        @JsonProperty("ticker")
        private String ticker;
        
        @JsonProperty("day")
        private DayData day;
        
        @JsonProperty("prevDay")
        private PrevDayData prevDay;
        
        @JsonProperty("min")
        private MinData min;
    }
    
    @Data
    public static class DayData {
        @JsonProperty("o")
        private Double open;      // 시가
        
        @JsonProperty("h")
        private Double high;      // 고가
        
        @JsonProperty("l")
        private Double low;       // 저가
        
        @JsonProperty("c")
        private Double close;     // 종가 (현재가)
        
        @JsonProperty("v")
        private Long volume;      // 거래량
        
        @JsonProperty("vw")
        private Double volumeWeightedAveragePrice;  // 가중 평균 가격
        
        @JsonProperty("t")
        private Long timestamp;   // 타임스탬프
    }
    
    @Data
    public static class PrevDayData {
        @JsonProperty("o")
        private Double open;      // 전일 시가
        
        @JsonProperty("h")
        private Double high;      // 전일 고가
        
        @JsonProperty("l")
        private Double low;       // 전일 저가
        
        @JsonProperty("c")
        private Double close;      // 전일 종가
        
        @JsonProperty("v")
        private Long volume;      // 전일 거래량
    }
    
    @Data
    public static class MinData {
        @JsonProperty("o")
        private Double open;
        
        @JsonProperty("h")
        private Double high;
        
        @JsonProperty("l")
        private Double low;
        
        @JsonProperty("c")
        private Double close;
        
        @JsonProperty("v")
        private Long volume;
        
        @JsonProperty("t")
        private Long timestamp;
    }
}

