package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.TradeUpdatesEventHandler;
import com.curihous.qbit.realtime.websocket.AlpacaTradeUpdatesManager;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/**
 * Alpaca WebSocket 설정
 */
@Slf4j
@Configuration
public class AlpacaWebSocketConfig {

    @Bean
    public WebSocketClient alpacaWebSocketClient() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            
            container.setDefaultMaxSessionIdleTimeout(90000); // 90초
            container.setDefaultMaxTextMessageBufferSize(8192);
            container.setDefaultMaxBinaryMessageBufferSize(8192);
            
            log.info("Alpaca WebSocketClient 초기화 완료");
            return new StandardWebSocketClient(container);
        } catch (Exception e) {
            log.error("Alpaca WebSocketClient 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("WebSocketClient 초기화 실패", e);
        }
    }

    @Bean
    public AlpacaTradeUpdatesManager alpacaTradeUpdatesManager(
            TradeUpdatesEventHandler eventHandler,
            org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        return new AlpacaTradeUpdatesManager(eventHandler, redisTemplate);
    }
}
