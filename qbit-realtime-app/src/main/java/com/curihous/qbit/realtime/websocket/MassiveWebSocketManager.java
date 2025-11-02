package com.curihous.qbit.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.curihous.qbit.infra.massive.dto.websocket.MassiveTradeMessage;
import com.curihous.qbit.infra.massive.dto.websocket.MassiveQuoteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Massive.io(Polygon.io) WebSocket 매니저
 * 실시간 체결 데이터 및 호가 데이터를 수신하고 subscribers에게 브로드캐스트
 * 
 * 지원 방식: 단일 연결에서 여러 종목 동시 구독
 * 1. WebSocket 연결: wss://socket.polygon.io/stocks
 * 2. 인증 메시지 전송: {"action":"auth","params":"API_KEY"}
 * 3. 구독 메시지 전송: {"action":"subscribe","params":"T.AAPL"} (체결)
 *                      {"action":"subscribe","params":"Q.AAPL"} (호가)
 *   - T = Trade (체결), Q = Quote (호가)
 * 
 */
@Slf4j
@Component
public class MassiveWebSocketManager implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    
    // 체결 데이터 구독자
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> tradeSubscribers;
    
    // 호가 데이터 구독자
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> quoteSubscribers;
    
    // Massive.io WebSocket 세션 (단일 연결)
    private volatile WebSocketSession massiveSession;
    
    // 구독된 심볼 관리
    private final Set<String> subscribedSymbols;
    
    private volatile WebSocketClient webSocketClient;
    
    @Value("${massive.api-key}")
    private String apiKey;
    
    private static final String MASSIVE_WEBSOCKET_URL = "wss://socket.polygon.io/stocks";
    
    // 비동기 작업용 ExecutorService
    private final ExecutorService connectionExecutor;
    
    // 재시도 스케줄링용 ScheduledExecutorService
    private final ScheduledExecutorService retryScheduler;
    
    // 재연결 스케줄링용 ScheduledExecutorService
    private final ScheduledExecutorService reconnectExecutor;
    
    // 진행 중인 연결 작업 추적 (중복 연결 방지)
    private volatile CompletableFuture<WebSocketSession> connectionFuture;
    
    // 진행 중인 재시도 작업 추적 (취소용)
    private volatile ScheduledFuture<?> scheduledRetry;
    
    // 진행 중인 재연결 작업 추적 (취소용)
    private volatile ScheduledFuture<?> scheduledReconnect;
    
    // 재연결 지연 시간 
    private static final long RECONNECT_DELAY_MS = 5000; // 5초

    public MassiveWebSocketManager() {
        this.objectMapper = new ObjectMapper();
        this.tradeSubscribers = new ConcurrentHashMap<>();
        this.quoteSubscribers = new ConcurrentHashMap<>();
        this.subscribedSymbols = ConcurrentHashMap.newKeySet();
        this.connectionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "massive-websocket-connection");
            t.setDaemon(true);
            return t;
        });
        this.retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "massive-websocket-retry");
            t.setDaemon(true);
            return t;
        });
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "massive-websocket-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    // WebSocket 연결 초기화
    public void initialize(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        log.info("Massive WebSocket 매니저 초기화 완료");
    }
    
    // 심볼 정규화 (대문자로 통일)
    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        return symbol.toUpperCase(java.util.Locale.ROOT);
    }

    // Massive.io WebSocket 연결
    private CompletableFuture<WebSocketSession> connectToMassiveAsync() {
        // 이미 연결되어 있으면 완료된 Future 반환
        if (massiveSession != null && massiveSession.isOpen()) {
            log.debug("Massive WebSocket 이미 연결됨");
            return CompletableFuture.completedFuture(massiveSession);
        }
        
        // 진행 중인 연결 작업이 있는지 확인
        CompletableFuture<WebSocketSession> existingFuture = connectionFuture;
        if (existingFuture != null && !existingFuture.isDone()) {
            log.debug("이미 진행 중인 연결 작업이 있음");
            return existingFuture;
        }
        
        // 새로운 연결 작업 시작
        CompletableFuture<WebSocketSession> newFuture = CompletableFuture
                .supplyAsync(() -> {
                    return attemptConnection(1, 1000L);
                }, connectionExecutor)
                .thenCompose(session -> {
                    if (session != null) {
                        return CompletableFuture.completedFuture(session);
                    } else {
                        // 첫 시도 실패 시 비동기 재시도
                        return scheduleRetry(2, 2000L);
                    }
                });
        
        connectionFuture = newFuture;
        
        // 완료 후 connectionFuture 정리
        newFuture.whenComplete((session, throwable) -> {
            connectionFuture = null;
            if (session != null) {
                massiveSession = session;
                log.info("Massive WebSocket 연결 완료: sessionId={}", session.getId());
                sendAuthMessage(session);
            } else if (throwable != null) {
                log.error("Massive WebSocket 연결 최종 실패: error={}", throwable.getMessage(), throwable);
            }
        });
        
        return newFuture;
    }
    
    // 단일 연결 시도
    private WebSocketSession attemptConnection(int attempt, long backoffMs) {
        try {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            
            log.info("Massive WebSocket 연결 시도 {}: url={}", attempt, MASSIVE_WEBSOCKET_URL);
            
            WebSocketSession session = webSocketClient
                    .execute(this, headers, URI.create(MASSIVE_WEBSOCKET_URL))
                    .get(10, TimeUnit.SECONDS);
            
            log.info("Massive WebSocket 연결 성공: sessionId={}", session.getId());
            return session;
            
        } catch (TimeoutException e) {
            log.error("Massive WebSocket 연결 타임아웃 (시도 {}): error={}", attempt, e.getMessage());
            return null;
        } catch (ExecutionException e) {
            log.error("Massive WebSocket 연결 실패 (시도 {}): error={}", attempt, e.getMessage(), e);
            return null;
        } catch (InterruptedException e) {
            log.error("Massive WebSocket 연결 중단됨 (시도 {}): error={}", attempt, e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    // 비동기 재시도 스케줄링 (지수 백오프)
    private CompletableFuture<WebSocketSession> scheduleRetry(int attempt, long backoffMs) {
        if (attempt > 3) {
            log.error("Massive WebSocket 연결 실패: 최대 재시도 횟수(3)를 초과했습니다.");
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<WebSocketSession> future = new CompletableFuture<>();
        
        // ScheduledExecutorService로 지연 후 재시도
        scheduledRetry = retryScheduler.schedule(() -> {
            try {
                WebSocketSession session = attemptConnection(attempt, backoffMs);
                
                if (session != null) {
                    future.complete(session);
                } else {
                    // 재시도 실패 시 다음 재시도 스케줄링
                    long nextBackoffMs = backoffMs * 2;
                    scheduleRetry(attempt + 1, nextBackoffMs)
                            .thenAccept(future::complete)
                            .exceptionally(throwable -> {
                                future.completeExceptionally(throwable);
                                return null;
                            });
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, backoffMs, TimeUnit.MILLISECONDS);
        
        return future;
    }

    // 인증 메시지 전송
    private void sendAuthMessage(WebSocketSession session) {
        try {
            String authMessage = String.format("{\"action\":\"auth\",\"params\":\"%s\"}", apiKey);
            session.sendMessage(new TextMessage(authMessage));
            log.info("Massive WebSocket 인증 메시지 전송 완료: sessionId={}", session.getId());
        } catch (IOException e) {
            log.error("Massive WebSocket 인증 메시지 전송 실패: sessionId={}, error={}", 
                    session.getId(), e.getMessage());
        }
    }

    // 구독 메시지 전송
    private void sendSubscribeMessage(WebSocketSession session, String streamType, String ticker) {
        try {
            // T.AAPL, Q.AAPL 형식
            String subscribeMessage = String.format("{\"action\":\"subscribe\",\"params\":\"%s.%s\"}", 
                    streamType, ticker);
            session.sendMessage(new TextMessage(subscribeMessage));
            log.info("Massive WebSocket 구독 메시지 전송: stream={}, ticker={}, sessionId={}", 
                    streamType, ticker, session.getId());
        } catch (IOException e) {
            log.error("Massive WebSocket 구독 메시지 전송 실패: stream={}, ticker={}, error={}", 
                    streamType, ticker, e.getMessage());
        }
    }

    // 구독 해제 메시지 전송
    private void sendUnsubscribeMessage(WebSocketSession session, String streamType, String ticker) {
        try {
            String unsubscribeMessage = String.format("{\"action\":\"unsubscribe\",\"params\":\"%s.%s\"}", 
                    streamType, ticker);
            session.sendMessage(new TextMessage(unsubscribeMessage));
            log.info("Massive WebSocket 구독 해제 메시지 전송: stream={}, ticker={}, sessionId={}", 
                    streamType, ticker, session.getId());
        } catch (IOException e) {
            log.error("Massive WebSocket 구독 해제 메시지 전송 실패: stream={}, ticker={}, error={}", 
                    streamType, ticker, e.getMessage());
        }
    }

    // 종목 실시간 체결 데이터 subscribe
    public void subscribeToTrade(String ticker, WebSocketSession clientSession) {
        String normalizedTicker = normalizeSymbol(ticker);
        tradeSubscribers.computeIfAbsent(normalizedTicker, k -> new CopyOnWriteArraySet<>()).add(clientSession);
        
        // 첫 번째 구독자라면 Massive WebSocket 연결 및 구독
        if (tradeSubscribers.get(normalizedTicker).size() == 1) {
            ensureMassiveConnection();
            if (massiveSession != null && massiveSession.isOpen()) {
                sendSubscribeMessage(massiveSession, "T", normalizedTicker);
                subscribedSymbols.add("T." + normalizedTicker);
            }
        }
        
        log.info("체결 데이터 subscribe 추가: ticker={}, subscribers={}", normalizedTicker, 
                tradeSubscribers.get(normalizedTicker).size());
    }

    // 종목 실시간 호가 데이터 subscribe
    public void subscribeToQuote(String ticker, WebSocketSession clientSession) {
        String normalizedTicker = normalizeSymbol(ticker);
        quoteSubscribers.computeIfAbsent(normalizedTicker, k -> new CopyOnWriteArraySet<>()).add(clientSession);
        
        // 첫 번째 구독자라면 Massive WebSocket 연결 및 구독
        if (quoteSubscribers.get(normalizedTicker).size() == 1) {
            ensureMassiveConnection();
            if (massiveSession != null && massiveSession.isOpen()) {
                sendSubscribeMessage(massiveSession, "Q", normalizedTicker);
                subscribedSymbols.add("Q." + normalizedTicker);
            }
        }
        
        log.info("호가 데이터 subscribe 추가: ticker={}, subscribers={}", normalizedTicker, 
                quoteSubscribers.get(normalizedTicker).size());
    }

    // 종목 체결 데이터 subscribe 해제
    public void unsubscribeFromTrade(String ticker, WebSocketSession clientSession) {
        String normalizedTicker = normalizeSymbol(ticker);
        CopyOnWriteArraySet<WebSocketSession> subscribers = tradeSubscribers.get(normalizedTicker);
        if (subscribers != null) {
            subscribers.remove(clientSession);
            
            // 마지막 subscriber가 해제되면 구독 해제
            if (subscribers.isEmpty()) {
                tradeSubscribers.remove(normalizedTicker);
                if (massiveSession != null && massiveSession.isOpen()) {
                    sendUnsubscribeMessage(massiveSession, "T", normalizedTicker);
                    subscribedSymbols.remove("T." + normalizedTicker);
                }
            }
        }
        
        log.info("체결 데이터 subscribe 해제: ticker={}, subscribers={}", normalizedTicker, 
                tradeSubscribers.get(normalizedTicker) != null ? tradeSubscribers.get(normalizedTicker).size() : 0);
    }

    // 종목 호가 데이터 subscribe 해제
    public void unsubscribeFromQuote(String ticker, WebSocketSession clientSession) {
        String normalizedTicker = normalizeSymbol(ticker);
        CopyOnWriteArraySet<WebSocketSession> subscribers = quoteSubscribers.get(normalizedTicker);
        if (subscribers != null) {
            subscribers.remove(clientSession);
            
            // 마지막 subscriber가 해제되면 구독 해제
            if (subscribers.isEmpty()) {
                quoteSubscribers.remove(normalizedTicker);
                if (massiveSession != null && massiveSession.isOpen()) {
                    sendUnsubscribeMessage(massiveSession, "Q", normalizedTicker);
                    subscribedSymbols.remove("Q." + normalizedTicker);
                }
            }
        }
        
        log.info("호가 데이터 subscribe 해제: ticker={}, subscribers={}", normalizedTicker, 
                quoteSubscribers.get(normalizedTicker) != null ? quoteSubscribers.get(normalizedTicker).size() : 0);
    }

    // Massive WebSocket 연결 확인 및 연결 
    private void ensureMassiveConnection() {
        if (massiveSession == null || !massiveSession.isOpen()) {
            connectToMassiveAsync();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Massive WebSocket 연결 확립: sessionId={}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof TextMessage textMessage) {
            handleMassiveMessage(textMessage.getPayload());
        }
    }

    // Massive.io에서 받은 메시지 처리
    private void handleMassiveMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            
            // 인증 응답 처리
            if (root.has("ev") && "status".equals(root.path("ev").asText())) {
                String status = root.path("status").asText();
                String message_text = root.path("message").asText();
                log.info("Massive WebSocket 상태 메시지: status={}, message={}", status, message_text);
                
                if ("auth_success".equals(status)) {
                    log.info("Massive WebSocket 인증 성공");
                } else if ("auth_failed".equals(status)) {
                    log.error("Massive WebSocket 인증 실패: message={}", message_text);
                }
                return;
            }
            
            // 이벤트 타입 확인
            String eventType = root.path("ev").asText();
            String symbol = root.path("sym").asText();
            
            if ("T".equals(eventType)) {
                // 체결 데이터
                MassiveTradeMessage tradeMessage = objectMapper.readValue(message, MassiveTradeMessage.class);
                broadcastTradeData(symbol, tradeMessage);
            } else if ("Q".equals(eventType)) {
                // 호가 데이터
                MassiveQuoteMessage quoteMessage = objectMapper.readValue(message, MassiveQuoteMessage.class);
                broadcastQuoteData(symbol, quoteMessage);
            } else {
                log.debug("알 수 없는 이벤트 타입: ev={}, message={}", eventType, message);
            }
            
        } catch (Exception e) {
            log.error("Massive WebSocket 메시지 처리 실패: message={}, error={}", message, e.getMessage());
        }
    }

    // 특정 종목의 체결 데이터를 subscribers에게 브로드캐스트
    private void broadcastTradeData(String ticker, MassiveTradeMessage tradeMessage) {
        String normalizedTicker = normalizeSymbol(ticker);
        CopyOnWriteArraySet<WebSocketSession> subscribers = tradeSubscribers.get(normalizedTicker);
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
                        subscribers.remove(subscriber);
                        removedCount++;
                        log.debug("닫힌 세션 제거: sessionId={}", subscriber.getId());
                    }
                } catch (Exception e) {
                    log.warn("체결 데이터 전송 실패 (세션 제거): sessionId={}, error={}", 
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
                log.info("닫힌 세션 정리: ticker={}, removed={}, remaining={}", 
                        normalizedTicker, removedCount, subscribers.size());
            }
            
            log.debug("체결 데이터 브로드캐스트: ticker={}, active_subscribers={}", 
                    normalizedTicker, subscribers.size());
                    
        } catch (Exception e) {
            log.error("체결 데이터 브로드캐스트 실패: ticker={}, error={}", normalizedTicker, e.getMessage());
        }
    }
    
    // 특정 종목의 호가 데이터를 subscribers에게 브로드캐스트
    private void broadcastQuoteData(String ticker, MassiveQuoteMessage quoteMessage) {
        String normalizedTicker = normalizeSymbol(ticker);
        CopyOnWriteArraySet<WebSocketSession> subscribers = quoteSubscribers.get(normalizedTicker);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        
        try {
            String broadcastMessage = objectMapper.writeValueAsString(quoteMessage);
            int removedCount = 0;
            
            // 각 subscriber에게 전송
            for (WebSocketSession subscriber : subscribers) {
                try {
                    if (subscriber.isOpen()) {
                        subscriber.sendMessage(new TextMessage(broadcastMessage));
                    } else {
                        subscribers.remove(subscriber);
                        removedCount++;
                        log.debug("닫힌 세션 제거: sessionId={}", subscriber.getId());
                    }
                } catch (Exception e) {
                    log.warn("호가 데이터 전송 실패 (세션 제거): sessionId={}, error={}", 
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
                log.info("닫힌 세션 정리: ticker={}, removed={}, remaining={}", 
                        normalizedTicker, removedCount, subscribers.size());
            }
            
            log.debug("호가 데이터 브로드캐스트: ticker={}, active_subscribers={}", 
                    normalizedTicker, subscribers.size());
                    
        } catch (Exception e) {
            log.error("호가 데이터 브로드캐스트 실패: ticker={}, error={}", normalizedTicker, e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Massive WebSocket 전송 오류: sessionId={}, error={}", 
                session.getId(), exception.getMessage());
        handleDisconnectionAndReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.info("Massive WebSocket 연결 종료: sessionId={}, status={}", session.getId(), closeStatus);
        if (massiveSession == session) {
            massiveSession = null;
            subscribedSymbols.clear();
        }
        handleDisconnectionAndReconnect();
    }
    
    // 연결 해제 및 재연결 처리
    private void handleDisconnectionAndReconnect() {
        // 구독자가 있을 때만 재연결
        boolean hasSubscribers = !tradeSubscribers.isEmpty() || !quoteSubscribers.isEmpty();
        
        if (hasSubscribers) {
            log.info("Massive WebSocket 재연결 시도: tradeSubscribers={}, quoteSubscribers={}", 
                    tradeSubscribers.size(), quoteSubscribers.size());
            
            // 기존 재연결 작업 취소
            if (scheduledReconnect != null && !scheduledReconnect.isDone()) {
                scheduledReconnect.cancel(false);
            }
            
            // 비동기로 재연결 스케줄링 (WebSocket 콜백 스레드에서 블로킹하지 않음)
            scheduledReconnect = reconnectExecutor.schedule(() -> {
                connectToMassiveAsync().thenAccept(session -> {
                    if (session != null && session.isOpen()) {
                        // 기존 구독 복구
                        for (String symbol : tradeSubscribers.keySet()) {
                            sendSubscribeMessage(session, "T", symbol);
                            subscribedSymbols.add("T." + symbol);
                        }
                        for (String symbol : quoteSubscribers.keySet()) {
                            sendSubscribeMessage(session, "Q", symbol);
                            subscribedSymbols.add("Q." + symbol);
                        }
                        log.info("Massive WebSocket 재연결 성공 및 구독 복구 완료");
                    }
                }).exceptionally(throwable -> {
                    log.error("Massive WebSocket 재연결 실패: error={}", throwable.getMessage(), throwable);
                    return null;
                });
            }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
        } else {
            log.info("구독자가 없어 재연결하지 않음");
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    // 애플리케이션 종료 시 리소스 정리
    public void shutdown() {
        log.info("Massive WebSocket 매니저 종료 시작");
        
        // 진행 중인 연결 작업 취소
        if (connectionFuture != null && !connectionFuture.isDone()) {
            connectionFuture.cancel(true);
        }
        
        // 진행 중인 재시도 작업 취소
        if (scheduledRetry != null && !scheduledRetry.isDone()) {
            scheduledRetry.cancel(false);
        }
        
        // 진행 중인 재연결 작업 취소
        if (scheduledReconnect != null && !scheduledReconnect.isDone()) {
            scheduledReconnect.cancel(false);
        }
        
        // ExecutorService 종료
        connectionExecutor.shutdown();
        retryScheduler.shutdown();
        reconnectExecutor.shutdown();
        
        try {
            if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                connectionExecutor.shutdownNow();
            }
            if (!retryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
            if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            connectionExecutor.shutdownNow();
            retryScheduler.shutdownNow();
            reconnectExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // WebSocket 세션 종료
        if (massiveSession != null && massiveSession.isOpen()) {
            try {
                massiveSession.close();
            } catch (IOException e) {
                log.error("Massive WebSocket 세션 종료 실패: error={}", e.getMessage());
            }
        }
        
        log.info("Massive WebSocket 매니저 종료 완료");
    }
}

