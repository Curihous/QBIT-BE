package com.curihous.qbit.realtime.websocket;

import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.repository.AlpacaOAuthConnectionRepository;
import com.curihous.qbit.realtime.handler.TradeUpdatesEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AlpacaTradeUpdatesManager implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    
    // 사용자 ID별 Alpaca WebSocket 세션
    private final Map<Long, WebSocketSession> userSessions;
    
    // 세션 -> 사용자 ID 역매핑
    private final Map<WebSocketSession, Long> sessionToUserId;
    
    private final TradeUpdatesEventHandler eventHandler;
    private final AlpacaOAuthConnectionRepository alpacaOAuthConnectionRepository;
    
    private volatile WebSocketClient webSocketClient;
    
    private static final String ALPACA_STREAM_URL = "wss://paper-api.alpaca.markets/stream";
    
    private final ScheduledExecutorService reconnectScheduler = 
        Executors.newScheduledThreadPool(1);
    
    public AlpacaTradeUpdatesManager(TradeUpdatesEventHandler eventHandler,
                                     AlpacaOAuthConnectionRepository alpacaOAuthConnectionRepository) {
        this.objectMapper = new ObjectMapper();
        this.userSessions = new ConcurrentHashMap<>();
        this.sessionToUserId = new ConcurrentHashMap<>();
        this.eventHandler = eventHandler;
        this.alpacaOAuthConnectionRepository = alpacaOAuthConnectionRepository;
    }
    
    public void initialize(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        log.info("Alpaca Trade Updates WebSocket 매니저 초기화 완료");
    }
    
    // 로그인 시 자동 WebSocket 구독
    @EventListener
    public void handleLoginOrderSyncEvent(LoginOrderSyncEvent event) {
        log.info("로그인 이벤트 수신 - Alpaca WebSocket 구독 시작: userId={}", event.getUserId());
        subscribe(event.getUserId());
    }
    
    public void subscribe(Long userId) {
        if (userSessions.containsKey(userId)) {
            log.debug("이미 구독 중인 사용자: userId={}", userId);
            return;
        }
        
        // Alpaca OAuth 연결 조회
        AlpacaOAuthConnection connection = alpacaOAuthConnectionRepository
                .findByUserId(userId)
                .orElse(null);
        
        if (connection == null) {
            log.warn("Alpaca OAuth 연결 없음: userId={}", userId);
            return;
        }
        
        connectToAlpaca(userId, connection.getAccessToken());
    }
    
    // 구독 해제
    public void unsubscribe(Long userId) {
        WebSocketSession session = userSessions.remove(userId);
        
        if (session != null) {
            sessionToUserId.remove(session);
            if (session.isOpen()) {
                try {
                    session.close();
                    log.info("Alpaca Trade Updates 구독 해제: userId={}", userId);
                } catch (IOException e) {
                    log.error("Alpaca WebSocket 연결 해제 실패: userId={}, error={}", 
                            userId, e.getMessage());
                }
            }
        }
    }
    
    // 연결
    private void connectToAlpaca(Long userId, String accessToken) {
        String url = ALPACA_STREAM_URL;
        
        int maxAttempts = 3;
        int attempt = 0;
        long backoffMs = 1000;
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                log.info("Alpaca WebSocket 연결 시도 {}/{}: userId={}, url={}", 
                        attempt, maxAttempts, userId, url);
                
                WebSocketSession session = webSocketClient
                    .execute(this, new WebSocketHttpHeaders(), URI.create(url))
                    .get(10, TimeUnit.SECONDS);
                
                userSessions.put(userId, session);
                sessionToUserId.put(session, userId);
                
                log.info("Alpaca WebSocket 연결 성공: userId={}", userId);
                sendAuthMessage(session, accessToken);
                return;
                
            } catch (Exception e) {
                log.error("Alpaca WebSocket 연결 실패 (시도 {}/{}): userId={}, error={}", 
                        attempt, maxAttempts, userId, e.getMessage(), e);
                
                // 마지막 시도가 아니면 대기 후 재시도
                if (attempt < maxAttempts) {
                    try {
                        log.info("{}ms 후 재시도...", backoffMs);
                        Thread.sleep(backoffMs);
                        backoffMs *= 2; // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        
        log.error("Alpaca WebSocket 연결 실패: userId={}, 최대 재시도 횟수 초과", userId);
    }
    
    // 인증 메시지 전송
    private void sendAuthMessage(WebSocketSession session, String accessToken) {
        try {
            Map<String, Object> authMessage = Map.of(
                "action", "authenticate",
                "data", Map.of(
                    "key_id", accessToken,  
                    "secret_key", ""     
                )
            );
            
            String json = objectMapper.writeValueAsString(authMessage);
            session.sendMessage(new TextMessage(json));
            
            log.info("Alpaca 인증 메시지 전송 완료: sessionId={}", session.getId());
            
        } catch (IOException e) {
            log.error("Alpaca 인증 메시지 전송 실패: sessionId={}, error={}", 
                    session.getId(), e.getMessage());
        }
    }
    
    // 구독 메시지 전송
    private void sendListenMessage(WebSocketSession session) {
        try {
            Map<String, Object> listenMessage = Map.of(
                "action", "listen",
                "data", Map.of("streams", new String[]{"trade_updates"})
            );
            
            String json = objectMapper.writeValueAsString(listenMessage);
            session.sendMessage(new TextMessage(json));
            
            log.info("Alpaca trade_updates 구독 메시지 전송 완료: sessionId={}", session.getId());
            
        } catch (IOException e) {
            log.error("Alpaca 구독 메시지 전송 실패: sessionId={}, error={}", 
                    session.getId(), e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Alpaca WebSocket 연결 확립: sessionId={}", session.getId());
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (message instanceof TextMessage textMessage) {
            handleTextMessage(session, textMessage.getPayload());
        } else if (message instanceof BinaryMessage binaryMessage) {
            handleBinaryMessage(session, binaryMessage.getPayload().array());
        }
    }
    
    // 텍스트 메시지 처리
    private void handleTextMessage(WebSocketSession session, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String stream = root.path("stream").asText();
            
            log.debug("Alpaca 메시지 수신: stream={}, payload={}", stream, payload);
            
            switch (stream) {
                case "authentication":
                    handleAuthorizationMessage(session, root);
                    break;
                case "listening":
                    handleListeningMessage(root);
                    break;
                case "trade_updates":
                    handleTradeUpdatesMessage(session, root);
                    break;
                default:
                    log.warn("알 수 없는 스트림: stream={}, payload={}", stream, payload);
            }
            
        } catch (Exception e) {
            log.error("Alpaca 텍스트 메시지 처리 실패: payload={}, error={}", 
                    payload, e.getMessage(), e);
        }
    }
    
    // 바이너리 메시지 처리
    private void handleBinaryMessage(WebSocketSession session, byte[] payload) {
        try {
            String jsonString = new String(payload);
            JsonNode root = objectMapper.readTree(jsonString);
            
            String stream = root.path("stream").asText();
            if ("trade_updates".equals(stream)) {
                handleTradeUpdatesMessage(session, root);
            }
            
        } catch (Exception e) {
            log.error("Alpaca 바이너리 메시지 처리 실패: error={}", e.getMessage(), e);
        }
    }
    
    // 인증 응답 처리
    private void handleAuthorizationMessage(WebSocketSession session, JsonNode root) {
        String status = root.path("data").path("status").asText();
        String action = root.path("data").path("action").asText();
        
        log.info("Alpaca 인증 응답: status={}, action={}, sessionId={}", 
                status, action, session.getId());
        
        if ("authenticated".equals(status) && "authenticate".equals(action)) {
            log.info("Alpaca 인증 성공 - trade_updates 자동 구독됨: sessionId={}", session.getId());
        } else {
            log.error("Alpaca 인증 실패: status={}, action={}, sessionId={}", 
                    status, action, session.getId());
            // 인증 실패 시 연결 종료
            try {
                session.close();
            } catch (IOException e) {
                log.error("세션 종료 실패: {}", e.getMessage());
            }
        }
    }
    
    // 구독 확인 메시지 처리
    private void handleListeningMessage(JsonNode root) {
        JsonNode streamsNode = root.path("data").path("streams");
        log.info("Alpaca 구독 확인: streams={}", streamsNode);
    }
    
    // 체결 메시지 처리
    private void handleTradeUpdatesMessage(WebSocketSession session, JsonNode root) {
        Long userId = sessionToUserId.get(session);
        if (userId == null) {
            log.warn("사용자 ID를 찾을 수 없음: sessionId={}", session.getId());
            return;
        }
        
        try {
            JsonNode data = root.path("data");
            String event = data.path("event").asText();
            JsonNode orderNode = data.path("order");
            
            log.info("Trade Update 수신: userId={}, event={}, orderId={}", 
                    userId, event, orderNode.path("id").asText());
            
            // 이벤트 핸들러에 전달
            eventHandler.handleTradeUpdate(userId, event, data);
            
        } catch (Exception e) {
            log.error("Trade Updates 이벤트 처리 실패: userId={}, error={}", 
                    userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = sessionToUserId.get(session);
        log.error("Alpaca WebSocket 전송 오류: userId={}, error={}", 
                userId, exception.getMessage());
        handleDisconnectionAndReconnect(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        Long userId = sessionToUserId.get(session);
        log.info("Alpaca WebSocket 연결 종료: userId={}, status={}", userId, closeStatus);
        handleDisconnectionAndReconnect(session);
    }
    
    // 연결 해제 및 재연결 처리
    private void handleDisconnectionAndReconnect(WebSocketSession session) {
        Long userId = sessionToUserId.get(session);
        if (userId == null) {
            return;
        }
        // 세션 정리
        sessionToUserId.remove(session);
        userSessions.remove(userId);  
        
        // 재연결 시도
        log.info("Alpaca WebSocket 재연결 시도: userId={}", userId);
        reconnectScheduler.schedule(() -> {
            if (!userSessions.containsKey(userId)) {
                AlpacaOAuthConnection connection = alpacaOAuthConnectionRepository
                        .findByUserId(userId)
                        .orElse(null);
                
                if (connection != null) {
                    connectToAlpaca(userId, connection.getAccessToken());
                } else {
                    log.warn("재연결 실패: OAuth 연결 없음: userId={}", userId);
                }
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    // 종료(애플리케이션 종료 시)
    public void shutdown() {
        log.info("Alpaca WebSocket 매니저 종료 시작");
        // 모든 사용자 구독 해제
        userSessions.keySet().forEach(this::unsubscribe);
        // 스케줄러 종료
        reconnectScheduler.shutdown();
        try {
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Alpaca WebSocket 매니저 종료 완료");
    }
}

