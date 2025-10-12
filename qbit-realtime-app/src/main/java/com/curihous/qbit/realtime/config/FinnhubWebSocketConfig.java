package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.websocket.FinnhubWebSocketManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
@RequiredArgsConstructor
public class FinnhubWebSocketConfig {

    @Value("${finnhub.api.websocket-url}")
    private String websocketUrl;
    
    @Value("${finnhub.api.key}")
    private String apiKey;

    private final FinnhubWebSocketManager finnhubWebSocketManager;

    @Bean
    public WebSocketClient webSocketClient() {
        return new StandardWebSocketClient();
    }

    @Bean
    public FinnhubWebSocketManager finnhubWebSocketManager() {
        return new FinnhubWebSocketManager(websocketUrl, apiKey);
    }

    // 애플리케이션 시작 시 WebSocket 초기화
    @PostConstruct
    public void initializeWebSocket() {
        WebSocketClient client = webSocketClient();
        finnhubWebSocketManager.initialize(client);
    }
}
