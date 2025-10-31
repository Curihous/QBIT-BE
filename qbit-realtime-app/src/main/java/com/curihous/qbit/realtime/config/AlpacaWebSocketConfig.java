package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.TradeUpdatesEventHandler;
import com.curihous.qbit.realtime.websocket.AlpacaTradeUpdatesManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;

/**
 * Alpaca WebSocket 설정
 * 
 * alpacaWebSocketClient: AlpacaTradeUpdatesInitializer에서 주입받아 사용 (Jetty 11 기반)
 * alpacaTradeUpdatesManager: WebSocketConfig(STOMP 연결 시), LoginOrderSyncConsumer(로그인 시)에서 사용
 */
@Slf4j
@Configuration
public class AlpacaWebSocketConfig {

    @Bean(destroyMethod = "destroy")
    public WebSocketClient alpacaWebSocketClient() {
        try {
            org.eclipse.jetty.websocket.client.WebSocketClient jettyClient = new org.eclipse.jetty.websocket.client.WebSocketClient();
            jettyClient.start();

            log.info("Jetty WebSocket Client 초기화 완료");
            return new JettyWebSocketClientWrapper(jettyClient);
        } catch (Exception e) {
            log.error("Jetty WebSocket Client 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Jetty WebSocket Client 초기화 실패", e);
        }
    }

    @Bean
    public AlpacaTradeUpdatesManager alpacaTradeUpdatesManager(
            TradeUpdatesEventHandler eventHandler) {
        return new AlpacaTradeUpdatesManager(eventHandler);
    }
}

