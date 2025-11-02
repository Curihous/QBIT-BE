package com.curihous.qbit.infra.massive.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Massive.io(Polygon.io) WebSocket 거래 데이터 DTO
 * 
 * WebSocket: wss://socket.polygon.io/stocks
 * 
 * 연결 후 구독 절차(심볼당 별도 연결이 가능한 바이낸스와 달리 단일 연결 후 메세지로 구독):
 * 1. 인증: {"action":"auth","params":"API_KEY"}
 * 2. 구독: {"action":"subscribe","params":"T.AAPL"}
 *   - "T.AAPL" 형식: T(이벤트 타입) + "." + AAPL(종목 티커)
 *   - T = Trade (체결 데이터)
 * 
 */
@Data
public class MassiveTradeMessage {
    
    @JsonProperty("ev")
    private String eventType;  // "T" (Trade)
    
    @JsonProperty("sym")
    private String symbol;  // 종목 심볼 (예: "AAPL")
    
    @JsonProperty("x")
    private String exchange;  // 실제 거래 발생 거래소/ECN 코드 (예: "Q"=NYSE Arca, "B"=NASDAQ BX, "D"=ADF, "I"=IEX 등)
    
    @JsonProperty("i")
    private Long tradeId;  // 거래 ID
    
    @JsonProperty("p")
    private Double price;  // 체결가
    
    @JsonProperty("s")
    private Integer size;  // 체결 수량
    
    @JsonProperty("t")
    private Long timestamp;  // 거래 시간 (Unix timestamp, 나노초)
    
    @JsonProperty("c")
    private Integer[] conditions;  // 거래 조건 배열
    
    @JsonProperty("z")
    private Integer tape;  // 테이프 (종목 상장 거래소 그룹): 1=NYSE, 2=NYSE American/기타 지역 거래소, 3=NASDAQ
    // 참고: exchange는 실제 거래 발생 거래소, tape는 종목이 상장된 메인 거래소 그룹 (서로 다를 수 있음)
}

