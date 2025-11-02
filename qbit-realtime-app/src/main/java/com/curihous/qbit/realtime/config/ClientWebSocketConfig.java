package com.curihous.qbit.realtime.config;

import com.curihous.qbit.realtime.handler.ClientCryptoDepthWebSocketHandler;
import com.curihous.qbit.realtime.handler.ClientUsEquityQuoteWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Raw WebSocket 설정 (실시간 시장 데이터용)
 * 
 * Binance (암호화폐):
 * - /ws/depth/{binanceSymbol} 
 *   → 전체 호가창 (매수/매도 여러 레벨)
 * 
 * Massive.io (미국 주식):
 * - /ws/us-equity/quote/{ticker}
 *   → Level1 호가창 (NBBO: 최고 매수/최저 매도 1개씩) 
 * 
 * 바이낸스는 인증 불필요 (공개 데이터)
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ClientWebSocketConfig implements WebSocketConfigurer {

    private final ClientCryptoDepthWebSocketHandler clientCryptoDepthWebSocketHandler;
    private final ClientUsEquityQuoteWebSocketHandler clientUsEquityQuoteWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Binance
        registry.addHandler(clientCryptoDepthWebSocketHandler, "/ws/depth/{binanceSymbol}")
                .setAllowedOriginPatterns("*");
        
        // Massive.io 
        registry.addHandler(clientUsEquityQuoteWebSocketHandler, "/ws/us-equity/quote/{ticker}")
                .setAllowedOriginPatterns("*");
    }
}

