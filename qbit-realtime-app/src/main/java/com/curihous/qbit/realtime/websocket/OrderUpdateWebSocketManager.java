package com.curihous.qbit.realtime.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 주문 업데이트 WebSocket 메시지 전송 (STOMP)
 * SimpMessagingTemplate을 사용하여 특정 사용자에게 메시지 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderUpdateWebSocketManager {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 특정 사용자에게 주문 업데이트 메시지 전송
     * STOMP destination: /user/{userId}/queue/orders
     */
    public void sendToUser(Long userId, Object message) {
        try {
            String destination = "/queue/orders";
            messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), 
                destination, 
                message
            );
            
            log.info("주문 업데이트 전송 성공: userId={}, event={}", 
                    userId, extractEventType(message));
                    
        } catch (Exception e) {
            log.error("주문 업데이트 전송 실패: userId={}, error={}", 
                    userId, e.getMessage(), e);
        }
    }

    // 메시지에서 이벤트 타입 추출 (로깅용)
    private String extractEventType(Object message) {
        if (message instanceof Map) {
            return String.valueOf(((Map<?, ?>) message).get("event"));
        }
        return "unknown";
    }
}

