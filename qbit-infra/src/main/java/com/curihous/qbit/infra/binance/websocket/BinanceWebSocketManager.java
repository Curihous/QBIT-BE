package com.curihous.qbit.infra.binance.websocket;

import com.curihous.qbit.infra.binance.dto.websocket.BinanceTradeMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketManager {

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private volatile WebSocketSession binanceSession;
    private volatile WebSocketClient webSocketClient;
    private final ConcurrentHashMap<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();

    // Binance WebSocket 연결
    // WebSocket URL: wss://stream.binance.com:9443/ws/{symbol}@trade
    public void connectToBinance() {
        try {
            if (binanceSession != null && binanceSession.isOpen()) {
                log.info("Binance WebSocket 이미 연결됨");
                return;
            }

            webSocketClient = new StandardWebSocketClient();
            String binanceWsUrl = "wss://stream.binance.com:9443/ws/btcusdt@trade";
            
            WebSocketHandler handler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    log.info("Binance WebSocket 연결 성공");
                    binanceSession = session;
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    if (message instanceof TextMessage) {
                        String payload = ((TextMessage) message).getPayload();
                        handleBinanceMessage(payload);
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    log.error("Binance WebSocket 전송 에러", exception);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    log.warn("Binance WebSocket 연결 종료: {}", closeStatus);
                    binanceSession = null;
                    
                    // 재연결 시도
                    scheduler.schedule(() -> connectToBinance(), 5, TimeUnit.SECONDS);
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            };

            webSocketClient.doHandshake(handler, null, new URI(binanceWsUrl));
            
        } catch (Exception e) {
            log.error("Binance WebSocket 연결 실패", e);
            scheduler.schedule(() -> connectToBinance(), 10, TimeUnit.SECONDS);
        }
    }

    // Binance 메시지 처리
    private void handleBinanceMessage(String payload) {
        try {
            BinanceTradeMessage tradeMessage = objectMapper.readValue(payload, BinanceTradeMessage.class);
            log.debug("Binance 거래 데이터 수신: symbol={}, price={}, quantity={}", 
                     tradeMessage.getSymbol(), tradeMessage.getPrice(), tradeMessage.getQuantity());
            
            // 클라이언트에게 브로드캐스트
            broadcastToSubscribers(tradeMessage);
            
        } catch (Exception e) {
            log.error("Binance 메시지 파싱 실패: {}", payload, e);
        }
    }

    private void broadcastToSubscribers(BinanceTradeMessage tradeMessage) {
        clientSessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                return true;
            }
            
            try {
                String response = objectMapper.writeValueAsString(tradeMessage);
                session.sendMessage(new TextMessage(response));
                return false;
            } catch (Exception e) {
                log.error("클라이언트 브로드캐스트 실패: {}", entry.getKey(), e);
                return true;
            }
        });
    }

    // 클라이언트 세션 추가
    public void addClientSession(String sessionId, WebSocketSession session) {
        clientSessions.put(sessionId, session);
        log.info("클라이언트 세션 추가: {}, 총 세션 수: {}", sessionId, clientSessions.size());
    }

    // 클라이언트 세션 제거
    public void removeClientSession(String sessionId) {
        clientSessions.remove(sessionId);
        log.info("클라이언트 세션 제거: {}, 총 세션 수: {}", sessionId, clientSessions.size());
    }
    
    // BinanceWebSocketManager 종료
    @PreDestroy
    public void shutdown() {
        log.info("BinanceWebSocketManager 종료 시작...");
        
        // WebSocket 세션 정리
        if (binanceSession != null && binanceSession.isOpen()) {
            try {
                binanceSession.close();
                log.info("Binance WebSocket 세션 종료 완료");
            } catch (Exception e) {
                log.error("Binance WebSocket 세션 종료 실패", e);
            }
        }
        
        // 클라이언트 세션 정리
        clientSessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (Exception e) {
                log.error("클라이언트 세션 종료 실패: {}", session.getId(), e);
            }
        });
        clientSessions.clear();
        
        // ScheduledExecutorService 종료
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ScheduledExecutorService가 5초 내에 종료되지 않아 강제 종료합니다.");
                scheduler.shutdownNow();
                
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("ScheduledExecutorService 강제 종료 실패");
                }
            }
            log.info("ScheduledExecutorService 종료 완료");
        } catch (InterruptedException e) {
            log.error("ScheduledExecutorService 종료 중 인터럽트 발생", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("BinanceWebSocketManager 종료 완료");
    }
}
