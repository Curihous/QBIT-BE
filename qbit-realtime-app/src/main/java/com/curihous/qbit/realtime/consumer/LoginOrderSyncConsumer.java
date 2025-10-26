package com.curihous.qbit.realtime.consumer;

import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import com.curihous.qbit.realtime.websocket.AlpacaTradeUpdatesManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Redis Streams Consumer
 * LoginOrderSyncEvent를 구독하여 Alpaca WebSocket 구독 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginOrderSyncConsumer implements StreamListener<String, ObjectRecord<String, LoginOrderSyncEvent>> {

    private final AlpacaTradeUpdatesManager alpacaTradeUpdatesManager;
    
    @Override
    public void onMessage(ObjectRecord<String, LoginOrderSyncEvent> message) {
        try {
            LoginOrderSyncEvent event = message.getValue();
            
            log.info("LoginOrderSyncEvent 수신: userId={}, hasAccessToken={}", 
                    event.getUserId(), event.getAccessToken() != null);
            
            // Alpaca WebSocket 구독
            if (event.getAccessToken() != null && !event.getAccessToken().isEmpty()) {
                alpacaTradeUpdatesManager.subscribe(event.getUserId(), event.getAccessToken());
            } else {
                log.warn("Access Token이 없어 Alpaca 구독을 건너뜁니다: userId={}", event.getUserId());
            }
            
        } catch (Exception e) {
            log.error("LoginOrderSyncEvent 처리 실패: messageId={}, error={}", 
                    message.getId(), e.getMessage(), e);
        }
    }
}
