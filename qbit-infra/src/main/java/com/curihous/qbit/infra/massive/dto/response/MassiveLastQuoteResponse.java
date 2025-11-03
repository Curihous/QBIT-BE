package com.curihous.qbit.infra.massive.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Massive 최근 거래/호가 응답 DTO
 * 차트 데이터 조회
 * 
 * 사용 API: GET /v2/last/trade/{ticker}
 * 사용 API: GET /v2/last/nbbo/{ticker}
 */
@Data
public class MassiveLastQuoteResponse {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("last")
    private LastTrade last;
    
    @JsonProperty("results")
    private LastQuoteResult results;
    
    @Data
    public static class LastTrade {
        @JsonProperty("price")
        private Double price;
        
        @JsonProperty("size")
        private Integer size;
        
        @JsonProperty("timestamp")
        private Long timestamp;
        
        @JsonProperty("conditions")
        private List<Integer> conditions;
        
        @JsonProperty("exchange")
        private Integer exchange;
    }
    
    @Data
    public static class LastQuoteResult {
        @JsonProperty("b")
        private QuoteLevel bid;
        
        @JsonProperty("a")
        private QuoteLevel ask;
        
        @JsonProperty("t")
        private Long timestamp;
    }
    
    @Data
    public static class QuoteLevel {
        @JsonProperty("p")
        private Double price;
        
        @JsonProperty("s")
        private Integer size;
    }
}

