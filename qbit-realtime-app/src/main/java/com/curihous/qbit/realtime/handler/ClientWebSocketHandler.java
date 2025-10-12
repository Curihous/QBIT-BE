package com.curihous.qbit.realtime.handler;

import com.curihous.qbit.realtime.websocket.FinnhubWebSocketManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWebSocketHandler extends TextWebSocketHandler {

    private final FinnhubWebSocketManager finnhubWebSocketManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().toString();
        String symbol = extractSymbolFromUri(uri);
        
        if (symbol != null) {
            finnhubWebSocketManager.subscribe(symbol, session);
            log.info("클라이언트 WebSocket 연결: symbol={}, sessionId={}", symbol, session.getId());
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
        String symbol = extractSymbolFromUri(uri);
        
        if (symbol != null) {
            finnhubWebSocketManager.unsubscribe(symbol, session);
            log.info("클라이언트 WebSocket 연결 종료: symbol={}, sessionId={}, status={}", 
                    symbol, session.getId(), status);
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
