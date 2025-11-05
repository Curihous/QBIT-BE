package com.curihous.qbit.realtime.consumer;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.realtime.websocket.OrderUpdateWebSocketManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams Consumer
 * Trade Update 이벤트를 구독하여 WebSocket으로 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderUpdateConsumer implements StreamListener<String, MapRecord<String, Object, Object>> {

    private final OrderUpdateWebSocketManager webSocketManager;
    
    @Override
    public void onMessage(MapRecord<String, Object, Object> message) {
        try {
            // MapRecord에서 value 필드 추출 
            Map<Object, Object> valueMap = message.getValue();
            Object valueObj = valueMap.get("value");
            
            if (valueObj == null) {
                log.warn("주문 업데이트 메시지에 value 필드가 없습니다: messageId={}", message.getId());
                return;
            }
            
            if (!(valueObj instanceof TradeUpdateEvent)) {
                log.warn("주문 업데이트 메시지의 value가 TradeUpdateEvent 타입이 아닙니다: messageId={}, type={}", 
                        message.getId(), valueObj.getClass().getName());
                return;
            }
            
            TradeUpdateEvent event = (TradeUpdateEvent) valueObj;
            
            log.info("주문 업데이트 이벤트 수신: userId={}, event={}, symbol={}, orderId={}", 
                    event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
            
            // WebSocket 메시지 생성
            Map<String, Object> wsMessage = buildWebSocketMessage(event);
            
            // WebSocket으로 전송
            webSocketManager.sendToUser(event.getUserId(), wsMessage);
            
        } catch (Exception e) {
            log.error("주문 업데이트 WebSocket 전송 실패: messageId={}, error={}", 
                    message.getId(), e.getMessage(), e);
        }
    }
    
    // WebSocket 메시지 생성
    private Map<String, Object> buildWebSocketMessage(TradeUpdateEvent event) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "order_update");
        message.put("event", event.getEvent());
        message.put("eventTimestamp", event.getEventTimestamp());
        
        // 기본 주문 정보
        Map<String, Object> order = new HashMap<>();
        order.put("alpacaOrderId", event.getAlpacaOrderId());
        order.put("symbol", event.getSymbol());
        order.put("side", event.getSide());
        order.put("status", event.getStatus());
        
        // 체결 정보
        order.put("filledQuantity", event.getFilledQuantity());
        order.put("filledAvgPrice", event.getFilledAvgPrice());
        order.put("filledAt", event.getFilledAt());
        
        // 이벤트별 추가 정보
        if (event.getEventQuantity() != null) {
            order.put("eventQuantity", event.getEventQuantity());
        }
        if (event.getEventPrice() != null) {
            order.put("eventPrice", event.getEventPrice());
        }
        if (event.getPositionQuantity() != null) {
            order.put("positionQuantity", event.getPositionQuantity());
        }
        
        message.put("order", order);
        
        // 전체 주문 JSON 
        if (event.getOrderJson() != null) {
            message.put("orderJson", event.getOrderJson());
        }
        
        return message;
    }
}

