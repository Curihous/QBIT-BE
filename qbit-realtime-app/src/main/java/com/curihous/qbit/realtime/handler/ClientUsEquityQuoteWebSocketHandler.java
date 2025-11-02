package com.curihous.qbit.realtime.handler;

import com.curihous.qbit.realtime.websocket.MassiveWebSocketManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 미국 주식 실시간 Level1 호가창(NBBO) WebSocket 핸들러 (Massive.io)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientUsEquityQuoteWebSocketHandler extends TextWebSocketHandler {

    private final MassiveWebSocketManager massiveWebSocketManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getUri() == null) {
            log.warn("URI가 null입니다");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        
        String uri = session.getUri().toString();
        String ticker = extractTickerFromUri(uri);
        
        if (ticker != null) {
            massiveWebSocketManager.subscribeToQuote(ticker, session);
            log.info("클라이언트 시세 WebSocket 연결: ticker={}, sessionId={}", ticker, session.getId());
        } else {
            log.warn("잘못된 URI: {}", uri);
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("클라이언트 시세 메시지 수신: {}", message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("시세 WebSocket 전송 오류: sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        if (session.getUri() == null) {
            return;
        }
        
        String uri = session.getUri().toString();
        String ticker = extractTickerFromUri(uri);
        
        if (ticker != null) {
            massiveWebSocketManager.unsubscribeFromQuote(ticker, session);
            log.info("클라이언트 시세 WebSocket 연결 종료: ticker={}, sessionId={}, status={}", 
                    ticker, session.getId(), status);
        }
    }

    // URI에서 종목 ticker 추출
    private String extractTickerFromUri(String uri) {
        if (uri == null) return null;
        
        String[] parts = uri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

