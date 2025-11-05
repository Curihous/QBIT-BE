package com.curihous.qbit.realtime.config;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.realtime.consumer.LoginOrderSyncConsumer;
import com.curihous.qbit.realtime.consumer.OrderUpdateConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

/**
 * Redis Streams 이벤트 구독 설정
 * 
 * 구독 Stream:
 * - trade-updates: TradeUpdateEvent 구독, OrderUpdateConsumer에서 처리
 * - login-order-sync: LoginOrderSyncEvent 구독, LoginOrderSyncConsumer에서 처리
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamsConfig {

    // Stream 키
    private static final String TRADE_UPDATE_STREAM = "trade-updates";
    private static final String LOGIN_SYNC_STREAM = "login-order-sync";
    
    // Consumer 그룹 및 이름
    private static final String CONSUMER_GROUP = "qbit-realtime-group";
    private static final String CONSUMER_TRADE = "qbit-realtime-consumer-trade";
    private static final String CONSUMER_LOGIN = "qbit-realtime-consumer-login";
    
    // Polling 설정
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(100);

    private final RedisConnectionFactory connectionFactory;
    private final OrderUpdateConsumer orderUpdateConsumer;
    private final LoginOrderSyncConsumer loginOrderSyncConsumer;

    // Trade Update Stream 구독
    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, TradeUpdateEvent>> tradeUpdateStreamContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(POLL_TIMEOUT)
                .targetType(TradeUpdateEvent.class)
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        createConsumerGroup(TRADE_UPDATE_STREAM);
        
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_TRADE),
                StreamOffset.create(TRADE_UPDATE_STREAM, ReadOffset.from(">")), // 처리되지 않은 새 메시지만 읽기
                orderUpdateConsumer
        );

        container.start();
        log.info("Trade Update Stream 구독 시작: stream={}, consumer={}", 
                TRADE_UPDATE_STREAM, CONSUMER_TRADE);

        return container;
    }

    // Login Order Sync Stream 구독
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> loginOrderSyncStreamContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(POLL_TIMEOUT)
                .build();

        var container = StreamMessageListenerContainer.<String, MapRecord<String, String, String>>create(connectionFactory, options);
        createConsumerGroup(LOGIN_SYNC_STREAM);
        
        // MapRecord를 직접 처리
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_LOGIN),
                StreamOffset.create(LOGIN_SYNC_STREAM, ReadOffset.from(">")),
                message -> {
                    try {
                        if (message instanceof MapRecord) {
                            MapRecord<String, String, String> mapRecord = (MapRecord<String, String, String>) message;
                            loginOrderSyncConsumer.onMessage(mapRecord);
                        } else {
                            log.warn("LoginOrderSyncEvent 메시지가 MapRecord 형식이 아닙니다: messageId={}, type={}", 
                                    message.getId(), message.getClass().getName());
                        }
                    } catch (Exception e) {
                        log.error("LoginOrderSyncEvent 처리 실패: messageId={}, error={}", 
                                message.getId(), e.getMessage(), e);
                    }
                }
        );

        container.start();
        log.info("Login Order Sync Stream 구독 시작: stream={}, consumer={}", 
                LOGIN_SYNC_STREAM, CONSUMER_LOGIN);

        return container;
    }

    // Consumer Group 생성 (없을 때만 생성)
    private void createConsumerGroup(String stream) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.streamCommands()
                    .xGroupCreate(stream.getBytes(), CONSUMER_GROUP, ReadOffset.from("0"), true);
            log.info("Consumer Group 생성: group={}, stream={}", CONSUMER_GROUP, stream);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            String errorMessage = cause != null ? cause.getMessage() : e.getMessage();
            
            // BUSYGROUP 오류는 정상 (그룹이 이미 존재함)
            if (cause instanceof io.lettuce.core.RedisBusyException || 
                (errorMessage != null && errorMessage.contains("BUSYGROUP"))) {
                log.debug("Consumer Group이 이미 존재: group={}, stream={}", CONSUMER_GROUP, stream);
            } else if (errorMessage != null && (errorMessage.contains("no such key") || errorMessage.contains("NOSTREAM"))) {
                log.warn("Stream이 존재하지 않아 Consumer Group을 생성할 수 없습니다: stream={}. " +
                        "첫 번째 메시지가 발행되면 자동으로 생성됩니다.", stream);
            } else {
                log.error("Consumer Group 생성 실패: group={}, stream={}, error={}", 
                        CONSUMER_GROUP, stream, errorMessage, e);
            }
        }
    }
}

