package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Alpaca Movers API 응답 DTO
 * 
 * Alpaca API: GET /v1beta1/screener/stocks/movers
 * QBIT API: GET /stocks/ranking/moving
 */
@Data
public class AlpacaMoversResponse {
    
    @JsonProperty("gainers")
    private List<MoverItem> gainers;
    
    @JsonProperty("losers")
    private List<MoverItem> losers;
    
    @Data
    public static class MoverItem {
        @JsonProperty("symbol")
        private String symbol;
        
        @JsonProperty("change")
        private Double change;
        
        @JsonProperty("change_percent")
        private Double changePercent;
        
        @JsonProperty("volume")
        private Long volume;
        
        @JsonProperty("price")
        private Double price;
    }
}

