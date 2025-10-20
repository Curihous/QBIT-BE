package com.curihous.qbit.realtime.config;

import com.curihous.qbit.infra.security.jwt.JwtUtil;
import com.curihous.qbit.infra.security.util.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket 설정
 * - JWT 인증 지원
 * - 사용자별 주문 업데이트 구독
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory message broker
        config.enableSimpleBroker("/topic", "/queue");
        // 클라이언트에서 서버로 메시지 보낼 때 prefix
        config.setApplicationDestinationPrefixes("/app");
        // 사용자별 메시지
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // STOMP 엔드포인트 등록
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();  // SockJS fallback
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // CONNECT 시 JWT 인증
                    String token = accessor.getFirstNativeHeader("Authorization");
                    
                    if (token != null && token.startsWith("Bearer ")) {
                        token = token.substring(7);
                        
                        try {
                            // JWT 검증
                            if (!jwtUtil.isAccessTokenExpired(token)) {
                                Authentication authentication = jwtUtil.getAuthentication(token);
                                
                                if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                                    Long userId = userDetails.getUserId();
                                    accessor.setUser(() -> String.valueOf(userId));
                                    log.info("STOMP WebSocket 인증 성공: userId={}", userId);
                                } else {
                                    log.warn("STOMP 인증 실패: 유효하지 않은 사용자 정보");
                                    throw new IllegalArgumentException("Invalid user");
                                }
                            } else {
                                log.warn("STOMP 인증 실패: 토큰 만료");
                                throw new IllegalArgumentException("Token expired");
                            }
                        } catch (Exception e) {
                            log.error("STOMP JWT 검증 실패: {}", e.getMessage());
                            throw new IllegalArgumentException("Authentication failed");
                        }
                    } else {
                        log.warn("STOMP 인증 실패: Authorization 헤더 없음");
                        throw new IllegalArgumentException("No authorization header");
                    }
                }
                
                return message;
            }
        });
    }
}

