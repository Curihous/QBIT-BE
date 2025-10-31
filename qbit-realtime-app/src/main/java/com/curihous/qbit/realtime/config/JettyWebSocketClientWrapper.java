package com.curihous.qbit.realtime.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.Session;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Jetty 11 WebSocket Client를 Spring WebSocketClient 인터페이스로 래핑
 */
@Slf4j
public class JettyWebSocketClientWrapper implements WebSocketClient {

    private final org.eclipse.jetty.websocket.client.WebSocketClient jettyClient;

    public JettyWebSocketClientWrapper(
            org.eclipse.jetty.websocket.client.WebSocketClient jettyClient) {
        this.jettyClient = jettyClient;
    }

    @Override
    public java.util.concurrent.CompletableFuture<WebSocketSession> execute(WebSocketHandler handler, WebSocketHttpHeaders headers, URI uri) {
        CompletableFuture<WebSocketSession> future = new CompletableFuture<>();

        try {
            JettyWebSocketAdapter adapter = new JettyWebSocketAdapter(handler, uri, future);
            org.eclipse.jetty.websocket.client.ClientUpgradeRequest request = new org.eclipse.jetty.websocket.client.ClientUpgradeRequest();
            
            if (headers != null) {
                headers.forEach((name, values) -> {
                    for (String value : values) {
                        request.setHeader(name, value);
                    }
                });
            }

            jettyClient.connect(adapter, uri, request);

        } catch (Exception e) {
            log.error("Jetty WebSocket 연결 시작 실패: uri={}, error={}", uri, e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    @Override
    public java.util.concurrent.CompletableFuture<WebSocketSession> execute(WebSocketHandler handler, String uriTemplate, Object... uriVariables) {
        URI uri = UriComponentsBuilder.fromUriString(uriTemplate).buildAndExpand(uriVariables).toUri();
        return execute(handler, new WebSocketHttpHeaders(), uri);
    }

    public void destroy() {
        try {
            if (jettyClient != null && jettyClient.isStarted()) {
                jettyClient.stop();
            }
            log.info("Jetty WebSocket Client 종료 완료");
        } catch (Exception e) {
            log.error("Jetty WebSocket Client 종료 실패: {}", e.getMessage(), e);
        }
    }

    private static class JettyWebSocketAdapter implements org.eclipse.jetty.websocket.api.WebSocketListener {
        private final WebSocketHandler springHandler;
        private final URI uri;
        private final CompletableFuture<WebSocketSession> future;
        private JettyWebSocketSessionAdapter springSession;
        private org.eclipse.jetty.websocket.api.Session jettySession;

        public JettyWebSocketAdapter(WebSocketHandler springHandler, URI uri, CompletableFuture<WebSocketSession> future) {
            this.springHandler = springHandler;
            this.uri = uri;
            this.future = future;
        }

        @Override
        public void onWebSocketConnect(Session jettySession) {
            try {
                this.jettySession = jettySession;
                springSession = new JettyWebSocketSessionAdapter(jettySession, uri);
                springHandler.afterConnectionEstablished(springSession);
                future.complete(springSession);
                log.info("Jetty WebSocket 연결 확립: uri={}, sessionId={}", uri, jettySession.hashCode());
            } catch (Exception e) {
                log.error("Spring WebSocketHandler 연결 처리 실패: {}", e.getMessage(), e);
                future.completeExceptionally(e);
            }
        }

        @Override
        public void onWebSocketText(String message) {
            // Alpaca의 텍스트 "ping" 메시지 처리 (keep-alive)
            if ("ping".equalsIgnoreCase(message.trim())) {
                try {
                    if (jettySession != null && jettySession.isOpen()) {
                        jettySession.getRemote().sendString("pong");
                        log.debug("Alpaca ping 수신 → pong 응답: sessionId={}", jettySession.hashCode());
                    }
                } catch (Exception e) {
                    log.error("pong 전송 실패: sessionId={}, error={}", 
                            jettySession != null ? jettySession.hashCode() : "unknown", e.getMessage());
                }
                return;
            }
            
            // 일반 텍스트 메시지 처리
            if (springSession != null) {
                try {
                    springHandler.handleMessage(springSession, new TextMessage(message));
                } catch (Exception e) {
                    log.error("Spring WebSocketHandler 텍스트 메시지 처리 실패: {}", e.getMessage(), e);
                }
            }
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            if (springSession != null) {
                try {
                    byte[] data = new byte[len];
                    System.arraycopy(payload, offset, data, 0, len);
                    springHandler.handleMessage(springSession, new BinaryMessage(data));
                } catch (Exception e) {
                    log.error("Spring WebSocketHandler 바이너리 메시지 처리 실패: {}", e.getMessage(), e);
                }
            }
        }


        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            if (springSession != null) {
                try {
                    CloseStatus closeStatus = new CloseStatus(statusCode, reason);
                    springHandler.afterConnectionClosed(springSession, closeStatus);
                } catch (Exception e) {
                    log.error("Spring WebSocketHandler 연결 종료 처리 실패: {}", e.getMessage(), e);
                }
            }
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            if (springSession != null) {
                try {
                    springHandler.handleTransportError(springSession, cause);
                } catch (Exception e) {
                    log.error("Spring WebSocketHandler 전송 오류 처리 실패: {}", e.getMessage(), e);
                }
            }
        }
    }

    private static class JettyWebSocketSessionAdapter implements WebSocketSession {
        private final Session jettySession;
        private final URI uri;

        public JettyWebSocketSessionAdapter(Session jettySession, URI uri) {
            this.jettySession = jettySession;
            this.uri = uri;
        }

        @Override
        public String getId() {
            return String.valueOf(jettySession.hashCode());
        }

        @Override
        public URI getUri() {
            return uri;
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }

        @Override
        public java.security.Principal getPrincipal() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
            // Jetty에서는 설정 불가
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
            // Jetty에서는 설정 불가
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public java.util.List<WebSocketExtension> getExtensions() {
            return java.util.Collections.emptyList();
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                jettySession.getRemote().sendString(textMessage.getPayload());
            } else if (message instanceof BinaryMessage) {
                BinaryMessage binaryMessage = (BinaryMessage) message;
                jettySession.getRemote().sendBytes(java.nio.ByteBuffer.wrap(binaryMessage.getPayload().array()));
            } else {
                throw new IllegalArgumentException("Unsupported message type: " + message.getClass());
            }
        }

        @Override
        public boolean isOpen() {
            return jettySession.isOpen();
        }

        @Override
        public void close() throws IOException {
            jettySession.close();
        }

        @Override
        public void close(CloseStatus status) throws IOException {
            jettySession.close(status.getCode(), status.getReason());
        }

        @Override
        public java.net.InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public java.net.InetSocketAddress getRemoteAddress() {
            return null;
        }
    }
}
