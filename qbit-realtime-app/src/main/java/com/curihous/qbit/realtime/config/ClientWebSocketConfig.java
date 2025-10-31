package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.ClientWebSocketHandler;
import com.curihous.qbit.realtime.handler.ClientDepthWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Raw WebSocket 설정 (Binance 실시간 데이터용)
 * 
 * - /ws/market/{binanceSymbol} (체결 데이터, ClientWebSocketHandler)
 * - /ws/depth/{binanceSymbol} (호가창 데이터, ClientDepthWebSocketHandler)
 * 
 * 인증 불필요 (공개 데이터)
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ClientWebSocketConfig implements WebSocketConfigurer {

    private final ClientWebSocketHandler clientWebSocketHandler;
    private final ClientDepthWebSocketHandler clientDepthWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 실시간 체결 데이터
        registry.addHandler(clientWebSocketHandler, "/ws/market/{binanceSymbol}")
                .setAllowedOriginPatterns("*");
        
        // 실시간 호가창 데이터
        registry.addHandler(clientDepthWebSocketHandler, "/ws/depth/{binanceSymbol}")
                .setAllowedOriginPatterns("*");
    }
}

