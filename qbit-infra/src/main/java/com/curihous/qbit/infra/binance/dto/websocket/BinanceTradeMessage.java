package com.curihous.qbit.infra.binance.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Binance WebSocket 거래 데이터 DTO
 * 
 * WebSocket: wss://stream.binance.com:9443/ws/{symbol}@trade
 */
@Data
public class BinanceTradeMessage {
    
    @JsonProperty("e")
    private String eventType;
    
    @JsonProperty("E")
    private Long eventTime;
    
    @JsonProperty("s")
    private String symbol;
    
    @JsonProperty("t")
    private Long tradeId;
    
    @JsonProperty("p")
    private String price;
    
    @JsonProperty("q")
    private String quantity;
    
    @JsonProperty("b")
    private Long buyerOrderId;
    
    @JsonProperty("a")
    private Long sellerOrderId;
    
    @JsonProperty("T")
    private Long tradeTime;
    
    @JsonProperty("m")
    private Boolean isBuyerMaker;
    
    @JsonProperty("M")
    private Boolean ignore;
}
