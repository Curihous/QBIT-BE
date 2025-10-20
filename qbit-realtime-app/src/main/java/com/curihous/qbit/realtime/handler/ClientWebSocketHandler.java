package com.curihous.qbit.realtime.handler;

import com.curihous.qbit.realtime.websocket.BinanceWebSocketManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWebSocketHandler extends TextWebSocketHandler {

    private final BinanceWebSocketManager binanceWebSocketManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().toString();
        String binanceSymbol = extractSymbolFromUri(uri);
        
        if (binanceSymbol != null) {
            binanceWebSocketManager.subscribeToTrade(binanceSymbol, session);
            log.info("클라이언트 체결 데이터 WebSocket 연결: binanceSymbol={}, sessionId={}", binanceSymbol, session.getId());
        } else {
            log.warn("잘못된 URI: {}", uri);
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("클라이언트 메시지 수신: {}", message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 전송 오류: sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String uri = session.getUri().toString();
        String binanceSymbol = extractSymbolFromUri(uri);
        
        if (binanceSymbol != null) {
            binanceWebSocketManager.unsubscribeFromTrade(binanceSymbol, session);
            log.info("클라이언트 체결 데이터 WebSocket 연결 종료: binanceSymbol={}, sessionId={}, status={}", 
                    binanceSymbol, session.getId(), status);
        }
    }

    // URI에서 종목 심볼 추출
    private String extractSymbolFromUri(String uri) {
        if (uri == null) return null;
        
        String[] parts = uri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
