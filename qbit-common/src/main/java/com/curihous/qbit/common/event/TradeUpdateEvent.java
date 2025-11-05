package com.curihous.qbit.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Alpaca Trade Update 이벤트
 * Redis Streams를 통해 전달되는 메시지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeUpdateEvent implements Serializable {
    
    private Long userId;
    private String event;  // new, fill, partial_fill, canceled, rejected 등
    private String alpacaOrderId;
    private String symbol;
    private String side;  // buy, sell
    private String status;
    
    // 체결 정보
    private String filledQuantity;
    private String filledAvgPrice;
    private String filledAt;
    
    // fill/partial_fill 이벤트 시 추가 정보
    private String eventQuantity;  // 이번 체결 수량
    private String eventPrice;     // 이번 체결 가격
    private String eventTimestamp; // 이번 체결 시간
    private String positionQuantity; // 현재 포지션 수량
    
    // 전체 order 정보
    private String orderJson;
    
    public static TradeUpdateEvent of(Long userId, String event, String alpacaOrderId,
                                     String symbol, String side, String status,
                                     String filledQty, String filledAvgPrice, String filledAt,
                                     String eventQty, String eventPrice, String eventTimestamp,
                                     String positionQty, String orderJson) {
        return new TradeUpdateEvent(
            userId, event, alpacaOrderId, symbol, side, status,
            filledQty, filledAvgPrice, filledAt,
            eventQty, eventPrice, eventTimestamp, positionQty,
            orderJson
        );
    }
}

