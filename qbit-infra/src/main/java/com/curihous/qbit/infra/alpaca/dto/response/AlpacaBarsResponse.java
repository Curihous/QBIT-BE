package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Alpaca Bars API 응답 DTO
 * 
 * Alpaca API: GET /v2/stocks/{symbol}/bars
 * QBIT API: GET /stocks/ranking/volume에서 계산용
 * QBIT API: GET /stocks/ranking/volatility에서 계산용
 */
@Data
public class AlpacaBarsResponse {
    
    @JsonProperty("bars")
    private List<Bar> bars;
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("next_page_token")
    private String nextPageToken;
    
    @Data
    public static class Bar {
        @JsonProperty("t")
        private String timestamp;
        
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
        
        @JsonProperty("n")
        private Long tradeCount;
        
        @JsonProperty("vw")
        private Double volumeWeightedAveragePrice;
    }
}

