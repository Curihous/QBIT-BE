package com.curihous.qbit.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.curihous.qbit.infra.binance.dto.websocket.BinanceTradeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Binance WebSocket 매니저
 * 실시간 체결 데이터를 수신하고 subscribers에게 브로드캐스트
 * 
 * 지원 방식: 멀티 스트림 (여러 암호화폐 동시 지원)
 */
@Slf4j
@Component
public class BinanceWebSocketManager implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> symbolSubscribers;
    private final Map<String, WebSocketSession> binanceSessions; // 심볼별 세션 관리
    
    private volatile WebSocketClient webSocketClient;

    public BinanceWebSocketManager() {
        this.objectMapper = new ObjectMapper();
        this.symbolSubscribers = new ConcurrentHashMap<>();
        this.binanceSessions = new ConcurrentHashMap<>();
    }

    // WebSocket 연결 초기화
    public void initialize(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        // 초기 연결은 하지 않고, subscribe 요청 시 동적으로 연결
        log.info("Binance WebSocket 매니저 초기화 완료");
    }

    // 특정 심볼에 대한 Binance WebSocket 연결
    private void connectToBinance(String symbol) {
        if (binanceSessions.containsKey(symbol)) {
            log.debug("이미 연결된 심볼: {}", symbol);
            return;
        }

        int maxAttempts = 3;
        int attempt = 0;
        long backoffMs = 1000; // 초기 대기 시간 1초
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                // Binance WebSocket URL 
                String url = String.format("wss://stream.binance.com:9443/ws/%s@trade", symbol.toLowerCase());
                WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
                
                log.info("Binance WebSocket 연결 시도 {}/{}: symbol={}, url={}", attempt, maxAttempts, symbol, url);
                
                // 타임아웃 설정 (10초)
                java.util.concurrent.Future<WebSocketSession> future = 
                    webSocketClient.doHandshake(this, headers, URI.create(url));
                
                WebSocketSession session = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                binanceSessions.put(symbol, session);
                
                log.info("Binance WebSocket 연결 성공: symbol={}", symbol);
                return;
                
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Binance WebSocket 연결 타임아웃 (시도 {}/{}): symbol={}, error={}", 
                         attempt, maxAttempts, symbol, e.getMessage());
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Binance WebSocket 연결 실패 (시도 {}/{}): symbol={}, error={}", 
                         attempt, maxAttempts, symbol, e.getMessage(), e);
            } catch (InterruptedException e) {
                log.error("Binance WebSocket 연결 중단됨 (시도 {}/{}): symbol={}, error={}", 
                         attempt, maxAttempts, symbol, e.getMessage());
                Thread.currentThread().interrupt();
                return;
            }
            
            // 마지막 시도가 아니면 대기 후 재시도
            if (attempt < maxAttempts) {
                try {
                    log.info("{}ms 후 재시도...", backoffMs);
                    Thread.sleep(backoffMs);
                    backoffMs *= 2; // 지수 백오프 (1초 → 2초 → 4초)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        log.error("Binance WebSocket 연결 실패: symbol={}, 최대 재시도 횟수({})를 초과했습니다.", symbol, maxAttempts);
    }

    // 종목 실시간 체결 데이터 subscribe
    public void subscribe(String symbol, WebSocketSession clientSession) {
        symbolSubscribers.computeIfAbsent(symbol, k -> new CopyOnWriteArraySet<>()).add(clientSession);
        
        // 첫 번째 구독자라면 Binance WebSocket 연결
        if (symbolSubscribers.get(symbol).size() == 1) {
            connectToBinance(symbol);
        }
        
        log.info("종목 subscribe 추가: symbol={}, subscribers={}", symbol, 
                symbolSubscribers.get(symbol).size());
    }

    // 종목 subscribe 해제
    public void unsubscribe(String symbol, WebSocketSession clientSession) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = symbolSubscribers.get(symbol);
        if (subscribers != null) {
            subscribers.remove(clientSession);
            
            // 마지막 subscriber가 해제되면 Binance 연결도 해제
            if (subscribers.isEmpty()) {
                disconnectFromBinance(symbol);
                symbolSubscribers.remove(symbol);
            }
        }
        
        log.info("종목 subscribe 해제: symbol={}, subscribers={}", symbol, 
                symbolSubscribers.get(symbol) != null ? symbolSubscribers.get(symbol).size() : 0);
    }

    // 특정 심볼의 Binance WebSocket 연결 해제
    private void disconnectFromBinance(String symbol) {
        WebSocketSession session = binanceSessions.remove(symbol);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                log.info("Binance WebSocket 연결 해제: symbol={}", symbol);
            } catch (Exception e) {
                log.error("Binance WebSocket 연결 해제 실패: symbol={}, error={}", symbol, e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket 연결 확립: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof TextMessage textMessage) {
            handleBinanceMessage(textMessage.getPayload());
        }
    }

    // Binance에서 받은 메시지 처리
    private void handleBinanceMessage(String message) {
        try {
            BinanceTradeMessage tradeMessage = objectMapper.readValue(message, BinanceTradeMessage.class);
            
            // Binance는 단일 거래 데이터를 직접 전송
            broadcastToSubscribers(tradeMessage.getSymbol(), tradeMessage);
            
        } catch (Exception e) {
            log.error("Binance 메시지 처리 실패: message={}, error={}", message, e.getMessage());
        }
    }

    // 특정 종목의 체결 데이터를 subscribers에게 브로드캐스트
    private void broadcastToSubscribers(String symbol, BinanceTradeMessage tradeMessage) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = symbolSubscribers.get(symbol);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        
        try {
            String broadcastMessage = objectMapper.writeValueAsString(tradeMessage);
            int removedCount = 0;
            
            // 각 subscriber에게 전송
            for (WebSocketSession subscriber : subscribers) {
                try {
                    if (subscriber.isOpen()) {
                        subscriber.sendMessage(new TextMessage(broadcastMessage));
                    } else {
                        // 닫힌 세션 제거
                        subscribers.remove(subscriber);
                        removedCount++;
                        log.debug("닫힌 세션 제거: sessionId={}", subscriber.getId());
                    }
                } catch (Exception e) {
                    // 전송 실패 시 세션 정리
                    log.warn("메시지 전송 실패 (세션 제거): sessionId={}, error={}", 
                            subscriber.getId(), e.getMessage());
                    try {
                        subscriber.close();
                    } catch (Exception closeEx) {
                        // 이미 닫혔을 수 있음
                    }
                    subscribers.remove(subscriber);
                    removedCount++;
                }
            }
            
            // 정리된 세션 로깅
            if (removedCount > 0) {
                log.info("닫힌 세션 정리: symbol={}, removed={}, remaining={}", 
                        symbol, removedCount, subscribers.size());
            }
            
            log.debug("체결 데이터 브로드캐스트: symbol={}, active_subscribers={}", 
                    symbol, subscribers.size());
                    
        } catch (Exception e) {
            log.error("브로드캐스트 실패: symbol={}, error={}", symbol, e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 전송 오류: {}", exception.getMessage());
        
        // 연결 재시도 - 해당 세션이 어떤 심볼인지 찾아서 재연결
        String symbol = findSymbolBySession(session);
        if (symbol != null) {
            log.info("Binance WebSocket 재연결 시도: symbol={}", symbol);
            binanceSessions.remove(symbol); // 기존 세션 제거
            connectToBinance(symbol); // 재연결
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.info("WebSocket 연결 종료: {}, status={}", session.getId(), closeStatus);
        
        // 해당 세션이 어떤 심볼인지 찾아서 재연결
        String symbol = findSymbolBySession(session);
        if (symbol != null) {
            log.info("Binance WebSocket 재연결 시도: symbol={}", symbol);
            binanceSessions.remove(symbol); // 기존 세션 제거
            connectToBinance(symbol); // 재연결
        }
    }

    // 세션으로부터 심볼 찾기
    private String findSymbolBySession(WebSocketSession session) {
        return binanceSessions.entrySet().stream()
            .filter(entry -> entry.getValue().equals(session))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
