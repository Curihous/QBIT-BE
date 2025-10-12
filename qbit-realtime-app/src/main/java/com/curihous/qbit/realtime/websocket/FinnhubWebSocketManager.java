package com.curihous.qbit.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.curihous.qbit.infra.finnhub.dto.websocket.FinnhubTradeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

// 실시간 체결 데이터를 수신하고 subscribers에게 브로드캐스트
@Slf4j
@Component
public class FinnhubWebSocketManager implements WebSocketHandler {

    private final String websocketUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> symbolSubscribers;
    
    private volatile WebSocketSession finnhubSession;
    private volatile WebSocketClient webSocketClient;

    public FinnhubWebSocketManager(String websocketUrl, String apiKey) {
        this.websocketUrl = websocketUrl;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.symbolSubscribers = new ConcurrentHashMap<>();
    }

    // WebSocket 연결 초기화
    public void initialize(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        connectToFinnhub();
    }

    // Finnhub WebSocket에 연결
    private void connectToFinnhub() {
        int maxAttempts = 3;
        int attempt = 0;
        long backoffMs = 1000; // 초기 대기 시간 1초
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                String url = websocketUrl + "?token=" + apiKey;
                WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
                
                log.info("Finnhub WebSocket 연결 시도 {}/{}: {}", attempt, maxAttempts, url);
                
                // 타임아웃 설정 (10초)
                java.util.concurrent.Future<WebSocketSession> future = 
                    webSocketClient.doHandshake(this, headers, URI.create(url));
                
                finnhubSession = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                
                log.info("Finnhub WebSocket 연결 성공: {}", url);
                return; // 연결 성공 시 즉시 반환
                
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Finnhub WebSocket 연결 타임아웃 (시도 {}/{}): {}", attempt, maxAttempts, e.getMessage());
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Finnhub WebSocket 연결 실패 (시도 {}/{}): {}", attempt, maxAttempts, e.getMessage(), e);
            } catch (InterruptedException e) {
                log.error("Finnhub WebSocket 연결 중단됨 (시도 {}/{}): {}", attempt, maxAttempts, e.getMessage());
                Thread.currentThread().interrupt(); // 인터럽트 상태 복원
                return; // 중단 시 재시도 중지
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
        
        log.error("Finnhub WebSocket 연결 실패: 최대 재시도 횟수({})를 초과했습니다.", maxAttempts);
    }

    // 종목 실시간 체결 데이터 subscribe
    public void subscribe(String symbol, WebSocketSession clientSession) {
        symbolSubscribers.computeIfAbsent(symbol, k -> new CopyOnWriteArraySet<>()).add(clientSession);
        
        // Finnhub에 subscribe 요청 전송
        subscribeToSymbol(symbol);
        
        log.info("종목 subscribe 추가: symbol={}, subscribers={}", symbol, 
                symbolSubscribers.get(symbol).size());
    }

    // 종목 subscribe 해제
    public void unsubscribe(String symbol, WebSocketSession clientSession) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = symbolSubscribers.get(symbol);
        if (subscribers != null) {
            subscribers.remove(clientSession);
            
            // 마지막 subscriber가 해제되면 Finnhub에서도 subscribe 해제
            if (subscribers.isEmpty()) {
                unsubscribeFromSymbol(symbol);
                symbolSubscribers.remove(symbol);
            }
        }
        
        log.info("종목 subscribe 해제: symbol={}, subscribers={}", symbol, 
                symbolSubscribers.get(symbol) != null ? symbolSubscribers.get(symbol).size() : 0);
    }

    // Finnhub에 종목 subscribe 요청
    private void subscribeToSymbol(String symbol) {
        if (finnhubSession != null && finnhubSession.isOpen()) {
            String subscribeMessage = String.format("{\"type\":\"subscribe\",\"symbol\":\"%s\"}", symbol);
            try {
                finnhubSession.sendMessage(new TextMessage(subscribeMessage));
                log.debug("Finnhub subscribe 요청 전송: {}", subscribeMessage);
            } catch (Exception e) {
                log.error("Finnhub subscribe 요청 실패: {}", e.getMessage());
            }
        }
    }

    // Finnhub에서 종목 subscribe 해제 요청
    private void unsubscribeFromSymbol(String symbol) {
        if (finnhubSession != null && finnhubSession.isOpen()) {
            String unsubscribeMessage = String.format("{\"type\":\"unsubscribe\",\"symbol\":\"%s\"}", symbol);
            try {
                finnhubSession.sendMessage(new TextMessage(unsubscribeMessage));
                log.debug("Finnhub unsubscribe 요청 전송: {}", unsubscribeMessage);
            } catch (Exception e) {
                log.error("Finnhub unsubscribe 요청 실패: {}", e.getMessage());
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
            handleFinnhubMessage(textMessage.getPayload());
        }
    }

    // Finnhub에서 받은 메시지 처리
    private void handleFinnhubMessage(String message) {
        try {
            FinnhubTradeMessage tradeMessage = objectMapper.readValue(message, FinnhubTradeMessage.class);
            String messageType = tradeMessage.type();
            
            switch (messageType) {
                case "trade" -> {
                    if (tradeMessage.data() != null) {
                        // 각 체결 데이터를 해당 종목 subscribers에게 브로드캐스트
                        for (FinnhubTradeMessage.TradeData tradeData : tradeMessage.data()) {
                            broadcastToSubscribers(tradeData.symbol(), tradeData);
                        }
                    }
                }
                case "ping" -> {

                    log.debug("Finnhub ping 수신");
                }
                case "subscribe" -> {
                    log.info("Finnhub 구독 확인: {}", message);
                }
                case "error" -> {
                    log.error("Finnhub 에러 수신: {}", message);
                }
                default -> {
                    log.warn("알 수 없는 Finnhub 메시지 타입: {}", messageType);
                }
            }
            
        } catch (Exception e) {
            log.error("Finnhub 메시지 처리 실패: message={}, error={}", message, e.getMessage());
        }
    }

    // 특정 종목의 체결 데이터를 subscribers에게 브로드캐스트
    private void broadcastToSubscribers(String symbol, FinnhubTradeMessage.TradeData tradeData) {
        CopyOnWriteArraySet<WebSocketSession> subscribers = symbolSubscribers.get(symbol);
        if (subscribers != null) {
            try {
                String broadcastMessage = objectMapper.writeValueAsString(tradeData);
                
                for (WebSocketSession subscriber : subscribers) {
                    if (subscriber.isOpen()) {
                        subscriber.sendMessage(new TextMessage(broadcastMessage));
                    }
                }
                
                log.debug("체결 데이터 브로드캐스트: symbol={}, subscribers={}", 
                        symbol, subscribers.size());
                        
            } catch (Exception e) {
                log.error("브로드캐스트 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 전송 오류: {}", exception.getMessage());
        
        // 연결 재시도
        if (session == finnhubSession) {
            log.info("Finnhub WebSocket 재연결 시도...");
            connectToFinnhub();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.info("WebSocket 연결 종료: {}, status={}", session.getId(), closeStatus);
        
        // Finnhub 세션이 종료되면 재연결
        if (session == finnhubSession) {
            log.info("Finnhub WebSocket 재연결 시도...");
            connectToFinnhub();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
