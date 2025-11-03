package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.ClientCryptoDepthWebSocketHandler;
import com.curihous.qbit.realtime.handler.ClientCryptoTickerWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Raw WebSocket 설정 (실시간 시장 데이터용)
 * Binance WebSocket 연결 설정
 * Massive.io는 클라이언트 직접 연결 필요 (서버 프록시 미지원)
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ClientWebSocketConfig implements WebSocketConfigurer {

    private final ClientCryptoDepthWebSocketHandler clientCryptoDepthWebSocketHandler;
    private final ClientCryptoTickerWebSocketHandler clientCryptoTickerWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 전체 호가창 (매수/매도 여러 레벨)
        registry.addHandler(clientCryptoDepthWebSocketHandler, "/ws/depth/{binanceSymbol}")
                .setAllowedOriginPatterns("*");
        // 실시간 시세 (24시간 통계: 현재가, 고가, 저가, 시가, 변동률 등)  
        registry.addHandler(clientCryptoTickerWebSocketHandler, "/ws/ticker/{binanceSymbol}")
                .setAllowedOriginPatterns("*");
        
    }
}

