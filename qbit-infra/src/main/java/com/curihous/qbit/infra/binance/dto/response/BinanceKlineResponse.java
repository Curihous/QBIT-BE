package com.curihous.qbit.infra.binance.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Binance Kline(캔들) 데이터 응답 DTO
 * 
 * 사용 API: GET /api/v3/klines
 */
@Data
public class BinanceKlineResponse {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("interval")
    private String interval;
    
    @JsonProperty("openTime")
    private Long openTime;
    
    @JsonProperty("open")
    private String open;
    
    @JsonProperty("high")
    private String high;
    
    @JsonProperty("low")
    private String low;
    
    @JsonProperty("close")
    private String close;
    
    @JsonProperty("volume")
    private String volume;
    
    @JsonProperty("closeTime")
    private Long closeTime;
    
    @JsonProperty("quoteAssetVolume")
    private String quoteAssetVolume;
    
    @JsonProperty("numberOfTrades")
    private Long numberOfTrades;
    
    @JsonProperty("takerBuyBaseAssetVolume")
    private String takerBuyBaseAssetVolume;
    
    @JsonProperty("takerBuyQuoteAssetVolume")
    private String takerBuyQuoteAssetVolume;
    
    @JsonProperty("ignore")
    private String ignore;
}
