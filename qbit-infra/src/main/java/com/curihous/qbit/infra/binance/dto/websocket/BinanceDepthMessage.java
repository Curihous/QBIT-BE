package com.curihous.qbit.infra.binance.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Binance WebSocket 호가창 데이터 DTO
 * 
 * WebSocket: wss://stream.binance.com:9443/ws/{symbol}@depth{levels}@100ms
 * 
 */
@Data
public class BinanceDepthMessage {
    
    @JsonProperty("lastUpdateId")
    private Long lastUpdateId;
    
    @JsonProperty("bids")
    private List<List<String>> bids;

    @JsonProperty("asks")
    private List<List<String>> asks;
}

