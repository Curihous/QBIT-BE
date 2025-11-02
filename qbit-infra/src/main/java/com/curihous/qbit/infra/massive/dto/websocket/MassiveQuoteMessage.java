package com.curihous.qbit.infra.massive.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Massive.io(Polygon.io) WebSocket 호가 데이터 DTO
 * 
 * WebSocket: wss://socket.polygon.io/stocks
 * 
 * 연결 후 구독 절차(심볼당 별도 연결이 가능한 바이낸스와 달리 단일 연결 후 메세지로 구독):
 * 1. 인증: {"action":"auth","params":"API_KEY"}
 * 2. 구독: {"action":"subscribe","params":"Q.AAPL"}
 *   - "Q.AAPL" 형식: Q(이벤트 타입) + "." + AAPL(종목 티커)
 *   - Q = Quote (호가 데이터)
 * 
 */
@Data
public class MassiveQuoteMessage {
    
    @JsonProperty("ev")
    private String eventType;  // "Q" (Quote)
    
    @JsonProperty("sym")
    private String symbol;  // 종목 심볼 (예: "AAPL")
    
    @JsonProperty("bx")
    private String bidExchange;  // 매수 호가 거래소/ECN 코드 (예: "Q"=NYSE Arca, "B"=NASDAQ BX 등)
    
    @JsonProperty("bp")
    private Double bidPrice;  // 매수 가격
    
    @JsonProperty("bs")
    private Integer bidSize;  // 매수 수량
    
    @JsonProperty("ax")
    private String askExchange;  // 매도 호가 거래소/ECN 코드 (예: "Q"=NYSE Arca, "B"=NASDAQ BX 등)
    
    @JsonProperty("ap")
    private Double askPrice;  // 매도 가격
    
    @JsonProperty("as")
    private Integer askSize;  // 매도 수량
    
    @JsonProperty("t")
    private Long timestamp;  // 호가 시간 (Unix timestamp, 나노초)
    
    @JsonProperty("c")
    private Integer[] conditions;  // 조건 배열
    
    @JsonProperty("z")
    private Integer tape;  // 테이프 (종목 상장 거래소 그룹): 1=NYSE, 2=NYSE American/기타 지역 거래소, 3=NASDAQ
    // 참고: bidExchange/askExchange는 실제 호가 발생 거래소, tape는 종목이 상장된 메인 거래소 그룹 (서로 다를 수 있음)
}

