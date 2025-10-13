package com.curihous.qbit.infra.binance.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Binance 24시간 통계 조회 응답 DTO
 * 
 * 사용 API: GET /api/v3/ticker/24hr
 */
@Data
public class BinanceTickerResponse {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("price")
    private String price;
    
    @JsonProperty("priceChange")
    private String priceChange;
    
    @JsonProperty("priceChangePercent")
    private String priceChangePercent;
    
    @JsonProperty("weightedAvgPrice")
    private String weightedAvgPrice;
    
    @JsonProperty("prevClosePrice")
    private String prevClosePrice;
    
    @JsonProperty("lastPrice")
    private String lastPrice;
    
    @JsonProperty("lastQty")
    private String lastQty;
    
    @JsonProperty("bidPrice")
    private String bidPrice;
    
    @JsonProperty("bidQty")
    private String bidQty;
    
    @JsonProperty("askPrice")
    private String askPrice;
    
    @JsonProperty("askQty")
    private String askQty;
    
    @JsonProperty("openPrice")
    private String openPrice;
    
    @JsonProperty("highPrice")
    private String highPrice;
    
    @JsonProperty("lowPrice")
    private String lowPrice;
    
    @JsonProperty("volume")
    private String volume;
    
    @JsonProperty("quoteVolume")
    private String quoteVolume;
    
    @JsonProperty("openTime")
    private Long openTime;
    
    @JsonProperty("closeTime")
    private Long closeTime;
    
    @JsonProperty("firstId")
    private Long firstId;
    
    @JsonProperty("lastId")
    private Long lastId;
    
    @JsonProperty("count")
    private Long count;
}
