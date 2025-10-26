package com.curihous.qbit.realtime.consumer;

import com.curihous.qbit.realtime.websocket.AlpacaTradeUpdatesManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis Streams Consumer
 * LoginOrderSyncEvent를 구독하여 Alpaca WebSocket 구독 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginOrderSyncConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final AlpacaTradeUpdatesManager alpacaTradeUpdatesManager;
    
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> fields = message.getValue();
            
            // 값에서 JSON 따옴표 제거
            String userIdStr = fields.get("userId").replaceAll("^\"|\"$", "");
            String accessToken = fields.get("accessToken").replaceAll("^\"|\"$", "");
            
            Long userId = Long.parseLong(userIdStr);
            
            log.info("LoginOrderSyncEvent 수신: userId={}, hasAccessToken={}", 
                    userId, accessToken != null && !accessToken.isEmpty());
            
            // Alpaca WebSocket 구독
            if (accessToken != null && !accessToken.isEmpty()) {
                alpacaTradeUpdatesManager.subscribe(userId, accessToken);
            } else {
                log.warn("Access Token이 없어 Alpaca 구독을 건너뜁니다: userId={}", userId);
            }
            
        } catch (Exception e) {
            log.error("LoginOrderSyncEvent 처리 실패: messageId={}, error={}", 
                    message.getId(), e.getMessage(), e);
        }
    }
}
