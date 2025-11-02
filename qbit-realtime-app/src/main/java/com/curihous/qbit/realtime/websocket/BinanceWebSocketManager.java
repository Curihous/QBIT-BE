package com.curihous.qbit.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 실시간 호가창 데이터를 수신하고 subscribers에게 브로드캐스트
 * 
 * 지원 방식: 멀티 스트림 (여러 암호화폐 동시 지원)
 * 스트림 타입: depth (호가창)
 */
@Slf4j
@Component
public class BinanceWebSocketManager implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    
    // 호가창 데이터 구독자
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> depthSubscribers;
    
    // 심볼별 Binance 세션 관리 (depth)
    private final Map<String, WebSocketSession> depthSessions;
    
    private volatile WebSocketClient webSocketClient;
    
    // 호가창 레벨 (5, 10, 20 중 선택)
    private static final int DEPTH_LEVELS = 10;
    
    // 업데이트 속도 (100ms or 1000ms)
    private static final String DEPTH_UPDATE_SPEED = "100ms";

    public BinanceWebSocketManager() {
        this.objectMapper = new ObjectMapper();
        this.depthSubscribers = new ConcurrentHashMap<>();
        this.depthSessions = new ConcurrentHashMap<>();
    }
    

    // WebSocket 연결 초기화
    public void initialize(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
        log.info("Binance WebSocket 매니저 초기화 완료 (depth levels={}, speed={})", DEPTH_LEVELS, DEPTH_UPDATE_SPEED);
    }
    
    // 심볼 정규화 (대소문자 일관성 보장)
    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        return symbol.toUpperCase(java.util.Locale.ROOT);
    }

    // 특정 심볼의 호가창 데이터에 대한 Binance WebSocket 연결
    private void connectToDepthStream(String symbol) {
        if (depthSessions.containsKey(symbol)) {
            log.debug("이미 연결된 호가창 스트림: {}", symbol);
            return;
        }

        // Binance WebSocket 연결
        int maxAttempts = 3;
        int attempt = 0;
        long backoffMs = 1000; // 초기 대기 시간 1초
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                // Binance WebSocket URL 생성
                String symbolLower = symbol.toLowerCase();
                String url = String.format("wss://stream.binance.com:9443/ws/%s@depth%d@%s", 
                    symbolLower, DEPTH_LEVELS, DEPTH_UPDATE_SPEED);
                WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
                
                log.info("Binance WebSocket 연결 시도 {}/{}: symbol={}, url={}", 
                    attempt, maxAttempts, symbol, url);
                
                // 타임아웃 설정 (10초)
                WebSocketSession session = webSocketClient
                    .execute(this, headers, URI.create(url))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
                
                depthSessions.put(symbol, session);
                
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
        
        log.error("Binance WebSocket 연결 실패: symbol={}, 최대 재시도 횟수({})를 초과했습니다.", 
            symbol, maxAttempts);
    }

    // 종목 실시간 호가창 데이터 subscribe
    public void subscribeToDepth(String symbol, WebSocketSession clientSession) {
        String normalizedSymbol = normalizeSymbol(symbol);
        depthSubscribers.computeIfAbsent(normalizedSymbol, k -> new CopyOnWriteArraySet<>()).add(clientSession);
        
        // 첫 번째 구독자라면 Binance WebSocket 연결
        if (depthSubscribers.get(normalizedSymbol).size() == 1) {
            connectToDepthStream(normalizedSymbol);
        }
        
        log.info("호가창 데이터 subscribe 추가: symbol={}, subscribers={}", normalizedSymbol, 
                depthSubscribers.get(normalizedSymbol).size());
    }

    // 종목 호가창 데이터 subscribe 해제
    public void unsubscribeFromDepth(String symbol, WebSocketSession clientSession) {
        String normalizedSymbol = normalizeSymbol(symbol);
        CopyOnWriteArraySet<WebSocketSession> subscribers = depthSubscribers.get(normalizedSymbol);
        if (subscribers != null) {
            subscribers.remove(clientSession);
            
            // 마지막 subscriber가 해제되면 Binance 연결도 해제
            if (subscribers.isEmpty()) {
                disconnectFromDepthStream(normalizedSymbol);
                depthSubscribers.remove(normalizedSymbol);
            }
        }
        
        log.info("호가창 데이터 subscribe 해제: symbol={}, subscribers={}", normalizedSymbol, 
                depthSubscribers.get(normalizedSymbol) != null ? depthSubscribers.get(normalizedSymbol).size() : 0);
    }

    // 특정 심볼의 호가창 스트림 연결 해제
    private void disconnectFromDepthStream(String symbol) {
        WebSocketSession session = depthSessions.remove(symbol);
        if (session != null) {
            if (session.isOpen()) {
                try {
                    session.close();
                    log.info("Binance 호가창 스트림 연결 해제: symbol={}", symbol);
                } catch (Exception e) {
                    log.error("Binance 호가창 스트림 연결 해제 실패: symbol={}, error={}", symbol, e.getMessage());
                }
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
            // depthSessions에서 세션에 해당하는 심볼 찾기
            String symbol = findSymbolBySession(session);
            if (symbol != null) {
                handleDepthMessage(symbol, textMessage.getPayload());
            }
        }
    }
    
    // 세션으로부터 심볼 찾기 (depthSessions 역조회)
    private String findSymbolBySession(WebSocketSession session) {
        return depthSessions.entrySet().stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // Binance에서 받은 호가창 메시지 처리
    private void handleDepthMessage(String symbol, String message) {
        try {
            com.curihous.qbit.infra.binance.dto.websocket.BinanceDepthMessage depthMessage = 
                objectMapper.readValue(message, com.curihous.qbit.infra.binance.dto.websocket.BinanceDepthMessage.class);
            broadcastDepthData(symbol, depthMessage);
        } catch (Exception e) {
            log.error("Binance 호가창 메시지 처리 실패: message={}, error={}", message, e.getMessage());
        }
    }

    // 특정 종목의 호가창 데이터를 subscribers에게 브로드캐스트
    private void broadcastDepthData(String symbol, com.curihous.qbit.infra.binance.dto.websocket.BinanceDepthMessage depthMessage) {
        String normalizedSymbol = normalizeSymbol(symbol);
        CopyOnWriteArraySet<WebSocketSession> subscribers = depthSubscribers.get(normalizedSymbol);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        
        try {
            com.curihous.qbit.realtime.dto.RealtimeDepthResponseDto responseDto = 
                com.curihous.qbit.realtime.dto.RealtimeDepthResponseDto.from(normalizedSymbol, depthMessage);
            String broadcastMessage = objectMapper.writeValueAsString(responseDto);
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
                    log.warn("호가창 데이터 전송 실패 (세션 제거): sessionId={}, error={}", 
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
                        normalizedSymbol, removedCount, subscribers.size());
            }
            
            // log.debug("호가창 데이터 브로드캐스트: symbol={}, active_subscribers={}", 
            //         normalizedSymbol, subscribers.size());
                    
        } catch (Exception e) {
            log.error("호가창 데이터 브로드캐스트 실패: symbol={}, error={}", normalizedSymbol, e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 전송 오류: {}", exception.getMessage());
        handleDisconnectionAndReconnect(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.info("WebSocket 연결 종료: {}, status={}", session.getId(), closeStatus);
        handleDisconnectionAndReconnect(session);
    }
    
    // 연결 해제 및 재연결 처리
    private void handleDisconnectionAndReconnect(WebSocketSession session) {
        // depthSessions에서 세션에 해당하는 심볼 찾기
        String symbol = findSymbolBySession(session);
        if (symbol == null) {
            return;
        }
        
        // 기존 세션 제거
        depthSessions.remove(symbol);
        
        // 구독자가 있을 때만 재연결
        CopyOnWriteArraySet<WebSocketSession> activeSubscribers = depthSubscribers.get(symbol);
        if (activeSubscribers != null && !activeSubscribers.isEmpty()) {
            log.info("Binance 호가창 스트림 재연결 시도: symbol={}, subscribers={}", 
                symbol, activeSubscribers.size());
            connectToDepthStream(symbol);
        } else {
            log.info("구독자가 없어 재연결하지 않음: symbol={}", symbol);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
