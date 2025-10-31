package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.TradeUpdatesEventHandler;
import com.curihous.qbit.realtime.websocket.AlpacaTradeUpdatesManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/**
 * Alpaca WebSocket 설정
 * 
 * alpacaWebSocketClient: AlpacaTradeUpdatesInitializer에서 주입받아 사용
 * alpacaTradeUpdatesManager: WebSocketConfig(STOMP 연결 시), LoginOrderSyncConsumer(로그인 시)에서 사용
 */
@Slf4j
@Configuration
public class AlpacaWebSocketConfig {

    /**
     * Alpaca WebSocket 클라이언트
     * 
     * Spring Framework 6.2에서 JettyWebSocketClient가 삭제되어 StandardWebSocketClient 사용
     * TLSv1.3/1.2 설정은 QbitRealtimeApplication.main()에서 시스템 프로퍼티로 강제됨
     */
    @Bean
    public WebSocketClient alpacaWebSocketClient() {
        log.info("StandardWebSocketClient 초기화 완료 (TLSv1.3/1.2는 시스템 프로퍼티로 강제됨)");
        return new StandardWebSocketClient();
    }

    /**
     * Alpaca 주문 업데이트 관리자
     */
    @Bean
    public AlpacaTradeUpdatesManager alpacaTradeUpdatesManager(
            TradeUpdatesEventHandler eventHandler) {
        return new AlpacaTradeUpdatesManager(eventHandler);
    }
}

