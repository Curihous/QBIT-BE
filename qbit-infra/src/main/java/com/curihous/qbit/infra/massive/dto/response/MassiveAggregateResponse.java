package com.curihous.qbit.infra.massive.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Massive 집계 데이터 응답 DTO
 * 
 * 사용 API: GET /v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}
 */
@Data
public class MassiveAggregateResponse {
    
    @JsonProperty("ticker")
    private String ticker;
    
    @JsonProperty("queryCount")
    private Integer queryCount;
    
    @JsonProperty("resultsCount")
    private Integer resultsCount;
    
    @JsonProperty("adjusted")
    private Boolean adjusted;
    
    @JsonProperty("results")
    private List<AggregateResult> results;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("count")
    private Integer count;
    
    @Data
    public static class AggregateResult {
        @JsonProperty("v")
        private Long volume;
        
        @JsonProperty("vw")
        private Double volumeWeightedPrice;
        
        @JsonProperty("o")
        private Double openPrice;
        
        @JsonProperty("c")
        private Double closePrice;
        
        @JsonProperty("h")
        private Double highPrice;
        
        @JsonProperty("l")
        private Double lowPrice;
        
        @JsonProperty("t")
        private Long timestamp;
        
        @JsonProperty("n")
        private Integer numberOfTransactions;
    }
}

