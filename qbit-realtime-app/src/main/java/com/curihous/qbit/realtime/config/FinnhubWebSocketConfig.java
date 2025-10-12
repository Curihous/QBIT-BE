package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.websocket.FinnhubWebSocketManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class FinnhubWebSocketConfig {

    @Value("${finnhub.api.websocket-url}")
    private String websocketUrl;
    
    @Value("${finnhub.api.key}")
    private String apiKey;

    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
    }

    @Bean
    public FinnhubWebSocketManager finnhubWebSocketManager(WebSocketClient webSocketClient) {
        FinnhubWebSocketManager manager = new FinnhubWebSocketManager(websocketUrl, apiKey);
        manager.initialize(webSocketClient);  
        return manager;
    }
}
