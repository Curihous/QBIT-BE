package com.curihous.qbit.realtime.handler;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.realtime.producer.TradeUpdateProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Alpaca Trade Updates 이벤트 핸들러
 * WebSocket으로 수신한 주문 업데이트를 Redis Streams에 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeUpdatesEventHandler {

    private final TradeUpdateProducer tradeUpdateProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Trade Update 이벤트 처리
    public void handleTradeUpdate(Long userId, String event, JsonNode data) {
        try {
            log.info("Trade Update 수신: userId={}, event={}", userId, event);
            
            JsonNode orderNode = data.path("order");
            
            // TradeUpdateEvent 생성
            TradeUpdateEvent tradeEvent = buildTradeUpdateEvent(userId, event, data, orderNode);
            
            // Redis Streams에 발행
            tradeUpdateProducer.publishTradeUpdate(tradeEvent);
            
        } catch (Exception e) {
            log.error("Trade Update 처리 실패: userId={}, event={}, error={}", 
                    userId, event, e.getMessage(), e);
        }
    }
    
    // TradeUpdateEvent 생성
    private TradeUpdateEvent buildTradeUpdateEvent(Long userId, String event, 
                                                   JsonNode data, JsonNode orderNode) {
        try {
            String alpacaOrderId = getTextOrEmpty(orderNode.path("id"));
            String symbol = getTextOrEmpty(orderNode.path("symbol"));
            String side = getTextOrEmpty(orderNode.path("side"));
            String status = getTextOrEmpty(orderNode.path("status"));
            
            // 체결 정보
            String filledQty = getTextOrEmpty(orderNode.path("filled_qty"));
            String filledAvgPrice = getTextOrEmpty(orderNode.path("filled_avg_price"));
            String filledAt = getTextOrEmpty(orderNode.path("filled_at"));
            
            // 이벤트별 추가 정보 (fill, partial_fill)
            String eventQty = getTextOrEmpty(data.path("qty"));
            String eventPrice = getTextOrEmpty(data.path("price"));
            String eventTimestamp = getTextOrEmpty(data.path("timestamp"));
            String positionQty = getTextOrEmpty(data.path("position_qty"));
            
            // 전체 order JSON
            String orderJson = objectMapper.writeValueAsString(orderNode);
            
            return TradeUpdateEvent.of(
                userId, event, alpacaOrderId, symbol, side, status,
                filledQty, filledAvgPrice, filledAt,
                eventQty, eventPrice, eventTimestamp, positionQty,
                orderJson
            );
            
        } catch (Exception e) {
            log.error("TradeUpdateEvent 생성 실패: userId={}, event={}, error={}", 
                    userId, event, e.getMessage(), e);
            throw new RuntimeException("TradeUpdateEvent 생성 실패", e);
        }
    }
    
    // JsonNode에서 텍스트를 추출하되, null이면 빈 문자열 반환
    private String getTextOrEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        String text = node.asText();
        return text != null ? text : "";
    }
}
