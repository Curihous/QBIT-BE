package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.websocket.BinanceWebSocketManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class BinanceWebSocketConfig {

    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
    }

    @Bean
    public BinanceWebSocketManager binanceWebSocketManager(WebSocketClient webSocketClient) {
        BinanceWebSocketManager manager = new BinanceWebSocketManager();
        manager.initialize(webSocketClient);  
        return manager;
    }
}
