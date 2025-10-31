package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.websocket.BinanceWebSocketManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/**
 * Binance WebSocket 설정
 * 
 * webSocketClient: binanceWebSocketManager 초기화에 사용
 * binanceWebSocketManager: ClientWebSocketHandler(체결), ClientDepthWebSocketHandler(호가창)에서 사용
 */
@Configuration
public class BinanceWebSocketConfig {

    /**
     * Binance WebSocket 클라이언트
     */
    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
    }

    /**
     * Binance 실시간 데이터 관리자
     */
    @Bean
    public BinanceWebSocketManager binanceWebSocketManager(WebSocketClient webSocketClient) {
        BinanceWebSocketManager manager = new BinanceWebSocketManager();
        manager.initialize(webSocketClient);  
        return manager;
    }
}
