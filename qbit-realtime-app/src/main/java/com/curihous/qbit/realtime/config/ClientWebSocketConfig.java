package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.ClientWebSocketHandler;
import com.curihous.qbit.realtime.handler.ClientDepthWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ClientWebSocketConfig implements WebSocketConfigurer {

    private final ClientWebSocketHandler clientWebSocketHandler;
    private final ClientDepthWebSocketHandler clientDepthWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 실시간 체결 데이터
        registry.addHandler(clientWebSocketHandler, "/ws/market/{symbol}")
                .setAllowedOriginPatterns("*");
        
        // 실시간 호가창 데이터
        registry.addHandler(clientDepthWebSocketHandler, "/ws/depth/{symbol}")
                .setAllowedOriginPatterns("*");
    }
}
