package com.curihous.qbit.infra.massive.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Massive 전일 종가 조회 응답 DTO
 * 전일 대비 얼마나 올랐나/내렸냐 판단하기 위함(바이낸스는 API에서 변동률 직접 제공하는데 massive는 안 함)
 * 
 * 사용 API: GET /v2/aggs/ticker/{ticker}/prev
 */
@Data
public class MassiveTickerResponse {
    
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

