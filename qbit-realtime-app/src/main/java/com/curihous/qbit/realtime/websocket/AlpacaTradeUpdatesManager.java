package com.curihous.qbit.realtime.websocket;

import com.curihous.qbit.realtime.handler.TradeUpdatesEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AlpacaTradeUpdatesManager implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    
    // 사용자 ID별 Alpaca WebSocket 세션
    private final Map<Long, WebSocketSession> userSessions;
    
    // 세션 -> 사용자 ID 역매핑
    private final Map<WebSocketSession, Long> sessionToUserId;
    
    // 사용자 ID별 OAuth Access Token 저장 (재연결 시 사용)
    // 메모리 캐시 + Redis 백업
    private final Map<Long, String> userAccessTokens;
    
    private final TradeUpdatesEventHandler eventHandler;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String REDIS_TOKEN_KEY_PREFIX = "alpaca:token:";
    private static final Duration TOKEN_TTL = Duration.ofDays(7); // 7일 TTL
    
    private volatile WebSocketClient webSocketClient;
    
    private static final String ALPACA_STREAM_URL = "wss://paper-api.alpaca.markets/stream";
    
    private final ScheduledExecutorService reconnectScheduler = 
        Executors.newScheduledThreadPool(1);
    
    // Heartbeat 스케줄러 (ping 전송용)
    private final ScheduledExecutorService heartbeatScheduler = 
        Executors.newScheduledThreadPool(1);
    
    // 세션별 heartbeat 작업 관리 (종료 시 취소용)
    private final Map<WebSocketSession, ScheduledFuture<?>> sessionHeartbeats = new ConcurrentHashMap<>();
    
    // 세션별 인증 완료 상태
    private final Map<WebSocketSession, Boolean> sessionAuthenticated = new ConcurrentHashMap<>();
    
    // 세션별 인증 타임아웃 fallback 작업 (인증 응답 시 취소용)
    private final Map<WebSocketSession, ScheduledFuture<?>> authFallbackTasks = new ConcurrentHashMap<>();
    
    public AlpacaTradeUpdatesManager(
            TradeUpdatesEventHandler eventHandler,
            RedisTemplate<String, Object> redisTemplate) {
        this.objectMapper = new ObjectMapper();
        this.userSessions = new ConcurrentHashMap<>();
        this.sessionToUserId = new ConcurrentHashMap<>();
        this.userAccessTokens = new ConcurrentHashMap<>();
        this.eventHandler = eventHandler;
        this.redisTemplate = redisTemplate;
    }
    
    public void initialize(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        log.info("Alpaca Trade Updates WebSocket 매니저 초기화 완료");
    }
    

    
    public void subscribe(Long userId, String accessToken) {
        // synchronized로 중복 구독 방지
        synchronized (this) {
            if (userSessions.containsKey(userId)) {
                log.debug("이미 구독 중인 사용자: userId={}", userId);
                return;
            }
            
            // Access Token 저장 (메모리 + Redis)
            saveToken(userId, accessToken);
            
            connectToAlpaca(userId, accessToken);
        }
    }
    
    // 저장된 토큰으로 자동 구독 (STOMP CONNECT 시 호출)
    // 메모리 → Redis 순으로 조회
    public void subscribeIfHasToken(Long userId) {
        // synchronized로 중복 구독 방지
        synchronized (this) {
            if (userSessions.containsKey(userId)) {
                log.debug("이미 구독 중인 사용자: userId={}", userId);
                return;
            }
            
            // 1. 메모리에서 토큰 확인
            String accessToken = userAccessTokens.get(userId);
            
            // 2. 메모리에 없으면 Redis에서 조회
            if (accessToken == null || accessToken.isEmpty()) {
                log.info("메모리에 토큰이 없음. Redis에서 조회 시도: userId={}", userId);
                
                try {
                    String redisKey = REDIS_TOKEN_KEY_PREFIX + userId;
                    Object tokenObj = redisTemplate.opsForValue().get(redisKey);
                    
                    if (tokenObj != null) {
                        accessToken = tokenObj.toString();
                        
                        // 메모리에도 저장 (다음 조회 시 빠르게 접근)
                        userAccessTokens.put(userId, accessToken);
                        
                        log.info("Redis에서 Alpaca 토큰 조회 성공: userId={}", userId);
                    } else {
                        log.info("Redis에도 토큰이 없음: userId={}", userId);
                        log.info("알림: 로그인 시 LoginOrderSyncEvent가 발행되어야 토큰이 저장됩니다. 5초 후 재시도 예정: userId={}", userId);
                        // 토큰이 없을 때 5초 후 재시도 (LoginOrderSyncEvent가 늦게 발행될 수 있음)
                        reconnectScheduler.schedule(() -> {
                            if (!userSessions.containsKey(userId)) {
                                log.info("토큰 재조회 및 구독 재시도: userId={}", userId);
                                subscribeIfHasToken(userId);
                            }
                        }, 5, TimeUnit.SECONDS);
                        return;
                    }
                } catch (Exception e) {
                    log.error("Redis에서 토큰 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
                    // 조회 실패 시에도 5초 후 재시도
                    reconnectScheduler.schedule(() -> {
                        if (!userSessions.containsKey(userId)) {
                            log.info("토큰 재조회 및 구독 재시도 (조회 실패 복구): userId={}", userId);
                            subscribeIfHasToken(userId);
                        }
                    }, 5, TimeUnit.SECONDS);
                    return;
                }
            }
            
            // 3. 토큰이 있으면 구독 시작
            if (accessToken != null && !accessToken.isEmpty()) {
                log.info("저장된 Access Token으로 Alpaca 구독 시작: userId={}, tokenSource={}", 
                        userId, userAccessTokens.containsKey(userId) ? "memory" : "redis");
                connectToAlpaca(userId, accessToken);
            } else {
                log.info("Alpaca 구독 불가: Access Token 없음: userId={}", userId);
                // 토큰이 비어있을 때도 5초 후 재시도
                reconnectScheduler.schedule(() -> {
                    if (!userSessions.containsKey(userId)) {
                        log.info("토큰 재조회 및 구독 재시도 (빈 토큰 복구): userId={}", userId);
                        subscribeIfHasToken(userId);
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }
    }
    
    // 구독 해제
    public void unsubscribe(Long userId) {
        synchronized (this) {
            WebSocketSession session = userSessions.remove(userId);
            
            if (session != null) {
                cleanupSession(session);
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
    }
    
    // 연결
    private void connectToAlpaca(Long userId, String accessToken) {
        // 재연결 전 기존 세션 정리 (다중 세션 방지)
        WebSocketSession existingSession = userSessions.get(userId);
        if (existingSession != null) {
            log.info("기존 세션 정리 중: userId={}, sessionId={}", userId, existingSession.getId());
            try {
                cleanupSession(existingSession);
                sessionToUserId.remove(existingSession);
                if (existingSession.isOpen()) {
                    existingSession.close();
                    log.info("기존 세션 종료 완료: userId={}, sessionId={}", userId, existingSession.getId());
                }
            } catch (IOException e) {
                log.warn("기존 세션 종료 실패: userId={}, sessionId={}, error={}", 
                        userId, existingSession.getId(), e.getMessage());
            }
            userSessions.remove(userId);
        }
        
        String url = ALPACA_STREAM_URL;
        
        int maxAttempts = 3;
        int attempt = 0;
        long backoffMs = 1000;
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                log.info("Alpaca WebSocket 연결 시도 {}/{}: userId={}, url={}", 
                        attempt, maxAttempts, userId, url);
                
                // 연결 타임아웃
                WebSocketSession session = webSocketClient
                    .execute(this, new WebSocketHttpHeaders(), URI.create(url))
                    .get(5, TimeUnit.SECONDS);
                
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
            // 인증 상태 초기화
            sessionAuthenticated.put(session, false);
            
            Map<String, Object> authMessage = Map.of(
                "action", "authenticate",
                "data", Map.of(
                    "oauth_token", accessToken
                )
            );
            
            String json = objectMapper.writeValueAsString(authMessage);
            
            log.info("Alpaca 인증 메시지 전송 시작: sessionId={}, message={}", session.getId(), json);
            session.sendMessage(new TextMessage(json));
            log.info("Alpaca 인증 메시지 전송 완료: sessionId={}", session.getId());
            
            // 인증 응답 확인을 위한 타임아웃 모니터링
            reconnectScheduler.schedule(() -> {
                Boolean authenticated = sessionAuthenticated.get(session);
                if (authenticated == null || !authenticated) {
                    log.error("인증 응답을 받지 못함: sessionId={}, isOpen={}, 연결 상태 확인 필요", 
                            session.getId(), session.isOpen());
                }
            }, 10, TimeUnit.SECONDS);
            
        } catch (IOException e) {
            log.error("Alpaca 인증 메시지 전송 실패: sessionId={}, error={}", 
                    session.getId(), e.getMessage());
        }
    }
    
    // 구독 메시지 전송
    private void sendListenMessage(WebSocketSession session) {
        try {
            if (!session.isOpen()) {
                log.warn("세션이 이미 종료되어 구독 메시지를 보낼 수 없음: sessionId={}", session.getId());
                return;
            }
            
            String json = """
                {
                  "action": "listen",
                  "data": {
                    "streams": ["trade_updates"]
                  }
                }
                """;
            
            log.info("Alpaca trade_updates 구독 메시지 전송: sessionId={}, message={}", session.getId(), json);
            session.sendMessage(new TextMessage(json));
            log.info("Alpaca trade_updates 구독 메시지 전송 완료: sessionId={}", session.getId());
            
            // 구독 메시지 전송 후 heartbeat 시작
            startHeartbeat(session);
            
        } catch (IOException e) {
            log.error("Alpaca 구독 메시지 전송 실패: sessionId={}, error={}", 
                    session.getId(), e.getMessage(), e);
        }
    }
    
    // Heartbeat 시작(30초 간격)
    private void startHeartbeat(WebSocketSession session) {
        // 기존 heartbeat가 있으면 취소
        ScheduledFuture<?> existingHeartbeat = sessionHeartbeats.remove(session);
        if (existingHeartbeat != null && !existingHeartbeat.isDone()) {
            existingHeartbeat.cancel(false);
            log.debug("기존 heartbeat 취소: sessionId={}", session.getId());
        }
        
        // 새 heartbeat 시작
        ScheduledFuture<?> heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("ping"));
                    log.debug("Alpaca ping 전송: sessionId={}", session.getId());
                } else {
                    log.debug("세션이 종료되어 ping 전송 중단: sessionId={}", session.getId());
                    stopHeartbeat(session);
                }
            } catch (IOException e) {
                log.error("Alpaca ping 전송 실패: sessionId={}, error={}", session.getId(), e.getMessage());
                stopHeartbeat(session);
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        sessionHeartbeats.put(session, heartbeatFuture);
        log.debug("Alpaca heartbeat 시작: sessionId={}, interval=30초", session.getId());
    }
    
    // Heartbeat 중지
    private void stopHeartbeat(WebSocketSession session) {
        ScheduledFuture<?> heartbeatFuture = sessionHeartbeats.remove(session);
        if (heartbeatFuture != null && !heartbeatFuture.isDone()) {
            heartbeatFuture.cancel(false);
            log.debug("Alpaca heartbeat 중지: sessionId={}", session.getId());
        }
    }
    
    // 세션 정리 (인증 상태, fallback 작업 등)
    private void cleanupSession(WebSocketSession session) {
        stopHeartbeat(session);
        sessionAuthenticated.remove(session);
        ScheduledFuture<?> fallbackTask = authFallbackTasks.remove(session);
        if (fallbackTask != null && !fallbackTask.isDone()) {
            fallbackTask.cancel(false);
            log.debug("인증 타임아웃 fallback 취소: sessionId={}", session.getId());
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Alpaca WebSocket 연결 확립: sessionId={}, uri={}, localAddress={}, remoteAddress={}", 
                session.getId(), session.getUri(), session.getLocalAddress(), session.getRemoteAddress());
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // 모든 메시지 수신을 로깅 (인증 응답 확인용)
        log.info("Alpaca WebSocket 메시지 수신: sessionId={}, type={}, payloadLength={}", 
                session.getId(), message.getClass().getSimpleName(), 
                message instanceof TextMessage ? ((TextMessage) message).getPayload().length() : 
                message instanceof BinaryMessage ? ((BinaryMessage) message).getPayloadLength() : 0);
        
        if (message instanceof TextMessage textMessage) {
            String payload = textMessage.getPayload();
            log.info("Alpaca 텍스트 메시지 원본: sessionId={}, payload={}", session.getId(), payload);
            handleTextMessage(session, payload);
        } else if (message instanceof BinaryMessage binaryMessage) {
            log.info("Alpaca 바이너리 메시지 수신: sessionId={}, length={}", 
                    session.getId(), binaryMessage.getPayloadLength());
            handleBinaryMessage(session, binaryMessage.getPayload().array());
        } else {
            log.warn("Alpaca 알 수 없는 메시지 타입: sessionId={}, type={}", 
                    session.getId(), message.getClass().getName());
        }
    }
    
    // 텍스트 메시지 처리
    private void handleTextMessage(WebSocketSession session, String payload) {
        try {
            log.info("Alpaca 원본 메시지 수신: sessionId={}, payload={}", session.getId(), payload);
            
            // 빈 메시지 체크
            if (payload == null || payload.trim().isEmpty()) {
                log.warn("Alpaca 빈 메시지 수신: sessionId={}", session.getId());
                return;
            }
            
            // Ping 메시지 처리 
            if ("ping".equalsIgnoreCase(payload.trim())) {
                log.debug("Alpaca ping 수신 → pong 응답: sessionId={}", session.getId());
                try {
                    session.sendMessage(new TextMessage("pong"));
                } catch (IOException e) {
                    log.error("Alpaca pong 전송 실패: sessionId={}, error={}", session.getId(), e.getMessage());
                }
                return;
            }
            
            // JSON 파싱 시도
            JsonNode root;
            try {
                root = objectMapper.readTree(payload);
            } catch (Exception e) {
                log.error("Alpaca 메시지 JSON 파싱 실패: sessionId={}, payload={}, error={}", 
                        session.getId(), payload, e.getMessage());
                return;
            }
            
            if (root.isArray()) {
                log.info("Alpaca 배열 형식 메시지 수신: sessionId={}, arraySize={}", session.getId(), root.size());
                for (JsonNode message : root) {
                    handleSingleMessage(session, message);
                }
                return;
            }
            
            // 단일 메시지 처리
            handleSingleMessage(session, root);
            
        } catch (Exception e) {
            log.error("Alpaca 텍스트 메시지 처리 실패: sessionId={}, payload={}, error={}", 
                    session.getId(), payload, e.getMessage(), e);
        }
    }
    
    // 단일 메시지 처리 (배열 내부 또는 단일 메시지)
    private void handleSingleMessage(WebSocketSession session, JsonNode root) {
        // 인증 응답 형식 1: {"T": "success", "msg": "authenticated"}
        String type = root.path("T").asText();
        if ("success".equals(type) && "authenticated".equals(root.path("msg").asText())) {
            log.info("Alpaca 인증 성공 (T=success): sessionId={}, payload={}", session.getId(), root.toString());
            handleAuthenticatedResponse(session);
            return;
        }
        
        // 인증 응답 형식 2: {"stream": "authorization", "data": {"status": "authorized", "action": "authenticate"}}
        String stream = root.path("stream").asText();
        
        if ("authorization".equals(stream)) {
            handleAuthorizationMessage(session, root);
            return;
        }
        
        // trade_updates 형식 1: {"T": "trade_updates", ...}
        if ("trade_updates".equals(type)) {
            log.info("Alpaca Trade Update 수신 (T=trade_updates): sessionId={}", session.getId());
            handleTradeUpdatesMessage(session, root);
            return;
        }
        
        // trade_updates 형식 2: {"stream": "trade_updates", ...}
        if ("trade_updates".equals(stream)) {
            log.info("Alpaca Trade Update 수신 (stream=trade_updates): sessionId={}", session.getId());
            handleTradeUpdatesMessage(session, root);
            return;
        }
        
        // listening 형식: {"stream": "listening", ...}
        if ("listening".equals(stream)) {
            handleListeningMessage(root);
            return;
        }
        
        log.warn("Alpaca 알 수 없는 메시지 형식: sessionId={}, T={}, stream={}, payload={}", 
                session.getId(), type, stream, root.toString());
    }
    
    // 인증 성공 응답 처리 (T=success 형태)
    private void handleAuthenticatedResponse(WebSocketSession session) {
        log.info("Alpaca 인증 성공 처리 시작: sessionId={}", session.getId());
        
        // 인증 완료 플래그 설정 및 fallback 취소
        sessionAuthenticated.put(session, true);
        ScheduledFuture<?> fallbackTask = authFallbackTasks.remove(session);
        if (fallbackTask != null && !fallbackTask.isDone()) {
            fallbackTask.cancel(false);
            log.debug("인증 타임아웃 fallback 취소: sessionId={}", session.getId());
        }
        
        reconnectScheduler.schedule(() -> {
            try {
                if (session.isOpen()) {
                    sendListenMessage(session);
                } else {
                    log.warn("세션이 이미 종료됨: sessionId={}", session.getId());
                }
            } catch (Exception e) {
                log.error("구독 메시지 전송 중 오류: sessionId={}, error={}", 
                        session.getId(), e.getMessage());
            }
        }, 300, TimeUnit.MILLISECONDS);
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
        
        if ("authorized".equals(status) && "authenticate".equals(action)) {
            log.info("Alpaca 인증 성공: sessionId={}", session.getId());
            
            // 인증 완료 플래그 설정 및 fallback 취소
            sessionAuthenticated.put(session, true);
            ScheduledFuture<?> fallbackTask = authFallbackTasks.remove(session);
            if (fallbackTask != null && !fallbackTask.isDone()) {
                fallbackTask.cancel(false);
                log.debug("인증 타임아웃 fallback 취소: sessionId={}", session.getId());
            }
            
            reconnectScheduler.schedule(() -> {
                try {
                    if (session.isOpen()) {
                        sendListenMessage(session);
                    } else {
                        log.warn("세션이 이미 종료됨: sessionId={}", session.getId());
                    }
                } catch (Exception e) {
                    log.error("구독 메시지 전송 중 오류: sessionId={}, error={}", 
                            session.getId(), e.getMessage());
                }
            }, 300, TimeUnit.MILLISECONDS);
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
            // trade_updates 형식에 따라 data 위치 찾기
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isEmpty()) {
                // T=trade_updates 형태일 경우 전체가 data일 수 있음
                data = root;
            }
            
            String event = data.path("event").asText();
            JsonNode orderNode = data.path("order");
            
            if (orderNode.isMissingNode() || orderNode.isEmpty()) {
                log.warn("Trade Update 메시지에 order 데이터 없음: sessionId={}, payload={}", 
                        session.getId(), root.toString());
                return;
            }
            
            String orderId = orderNode.path("id").asText();
            log.info("Trade Update 수신: userId={}, event={}, orderId={}", 
                    userId, event, orderId);
            
            // 이벤트 핸들러에 전달
            eventHandler.handleTradeUpdate(userId, event, data);
            
        } catch (Exception e) {
            log.error("Trade Updates 이벤트 처리 실패: userId={}, error={}, payload={}", 
                    userId, e.getMessage(), root.toString(), e);
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
    
    // 토큰 저장 (메모리 + Redis)
    private void saveToken(Long userId, String accessToken) {
        // 메모리에 저장
        userAccessTokens.put(userId, accessToken);
        
        // Redis에 저장 (7일 TTL)
        try {
            String redisKey = REDIS_TOKEN_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(redisKey, accessToken, TOKEN_TTL);
            log.debug("Alpaca 토큰 Redis 저장 완료: userId={}, ttl={}일", userId, TOKEN_TTL.toDays());
        } catch (Exception e) {
            log.error("Redis 토큰 저장 실패 (무시): userId={}, error={}", userId, e.getMessage());
            // Redis 저장 실패해도 메모리에 있으면 계속 사용 가능
        }
    }
    
    // 연결 해제 및 재연결 처리 (synchronized로 race condition 방지)
    private void handleDisconnectionAndReconnect(WebSocketSession session) {
        Long userId = sessionToUserId.get(session);
        if (userId == null) {
            return;
        }
        
        // 세션 정리
        cleanupSession(session);
        sessionToUserId.remove(session);
        userSessions.remove(userId);
        
        // synchronized로 중복 재연결 방지
        synchronized (this) {
            // 이미 재연결 중인지 확인
            if (userSessions.containsKey(userId)) {
                log.debug("이미 재연결 중이거나 연결됨: userId={}", userId);
                return;
            }
            
            // 재연결 시도 (메모리 → Redis 순으로 조회)
            String savedAccessToken = userAccessTokens.get(userId);
            
            if (savedAccessToken == null || savedAccessToken.isEmpty()) {
                // Redis에서 조회 시도
                try {
                    String redisKey = REDIS_TOKEN_KEY_PREFIX + userId;
                    Object tokenObj = redisTemplate.opsForValue().get(redisKey);
                    if (tokenObj != null) {
                        savedAccessToken = tokenObj.toString();
                        userAccessTokens.put(userId, savedAccessToken); 
                        log.info("Redis에서 토큰 조회 성공 (재연결용): userId={}", userId);
                    }
                } catch (Exception e) {
                    log.error("Redis 토큰 조회 실패 (재연결용): userId={}, error={}", userId, e.getMessage());
                }
            }
            
            // 람다에서 사용하기 위해 final 변수로 복사
            final String finalAccessToken = savedAccessToken;
            
            if (finalAccessToken == null || finalAccessToken.isEmpty()) {
                log.warn("재연결 실패: Access Token 없음: userId={}", userId);
                return;
            }
            
            log.info("Alpaca WebSocket 재연결 시도: userId={}", userId);
            reconnectScheduler.schedule(() -> {
                // 재연결 전 한 번 더 확인 (race condition 방지)
                synchronized (AlpacaTradeUpdatesManager.this) {
                    if (!userSessions.containsKey(userId)) {
                        subscribe(userId, finalAccessToken);
                    } else {
                        log.debug("재연결 스케줄 실행 시 이미 연결됨: userId={}", userId);
                    }
                }
            }, 5, TimeUnit.SECONDS);
        }
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    // 종료(애플리케이션 종료 시)
    public void shutdown() {
        log.info("Alpaca WebSocket 매니저 종료 시작");
        
        // 모든 heartbeat 중지
        sessionHeartbeats.values().forEach(heartbeat -> {
            if (heartbeat != null && !heartbeat.isDone()) {
                heartbeat.cancel(false);
            }
        });
        sessionHeartbeats.clear();
        
        // 모든 사용자 구독 해제
        userSessions.keySet().forEach(this::unsubscribe);
        
        // 스케줄러 종료
        reconnectScheduler.shutdown();
        heartbeatScheduler.shutdown();
        try {
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow();
            }
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectScheduler.shutdownNow();
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Alpaca WebSocket 매니저 종료 완료");
    }
}

