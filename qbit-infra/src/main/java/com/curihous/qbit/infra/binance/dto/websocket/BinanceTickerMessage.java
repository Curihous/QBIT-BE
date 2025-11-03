package com.curihous.qbit.infra.binance.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Binance WebSocket 24시간 통계 데이터 DTO (ticker 스트림)
 * 
 * WebSocket: wss://stream.binance.com:9443/ws/{symbol}@ticker
 * 
 * 실시간 24시간 통계 정보 제공 (고가, 저가, 시가, 변동률 등)
 */
@Data
public class BinanceTickerMessage {
    
    @JsonProperty("e")
    private String eventType;  // "24hrTicker"
    
    @JsonProperty("E")
    private Long eventTime;  // 이벤트 시간
    
    @JsonProperty("s")
    private String symbol;  // 종목 심볼
    
    @JsonProperty("p")
    private String priceChange;  // 변동가
    
    @JsonProperty("P")
    private String priceChangePercent;  // 변동률
    
    @JsonProperty("w")
    private String weightedAvgPrice;  // 가중 평균 가격
    
    @JsonProperty("x")
    private String prevClosePrice;  // 전일 종가
    
    @JsonProperty("c")
    private String lastPrice;  // 현재가 (최근 체결가)
    
    @JsonProperty("Q")
    private String lastQty;  // 최근 체결 수량
    
    @JsonProperty("b")
    private String bidPrice;  // 최우선 매수 가격
    
    @JsonProperty("B")
    private String bidQty;  // 최우선 매수 수량
    
    @JsonProperty("a")
    private String askPrice;  // 최우선 매도 가격
    
    @JsonProperty("A")
    private String askQty;  // 최우선 매도 수량
    
    @JsonProperty("o")
    private String openPrice;  // 시가
    
    @JsonProperty("h")
    private String highPrice;  // 고가
    
    @JsonProperty("l")
    private String lowPrice;  // 저가
    
    @JsonProperty("v")
    private String volume;  // 거래량
    
    @JsonProperty("q")
    private String quoteVolume;  // 거래대금
    
    @JsonProperty("O")
    private Long openTime;  // 통계 시작 시간
    
    @JsonProperty("C")
    private Long closeTime;  // 통계 종료 시간
    
    @JsonProperty("F")
    private Long firstTradeId;  // 첫 거래 ID
    
    @JsonProperty("L")
    private Long lastTradeId;  // 마지막 거래 ID
    
    @JsonProperty("n")
    private Long tradeCount;  // 거래 건수
}

