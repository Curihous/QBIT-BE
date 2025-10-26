package com.curihous.qbit.realtime.config;

import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.realtime.consumer.LoginOrderSyncConsumer;
import com.curihous.qbit.realtime.consumer.OrderUpdateConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

/**
 * Redis Streams 이벤트 구독 설정
 * 
 * 두 가지 이벤트를 구독:
 * 1. LoginOrderSyncEvent: 로그인 시 Alpaca WebSocket 구독 시작(주문 체결 정보 받기)
 * 2. TradeUpdateEvent: 주문 체결 정보 클라이언트에게 WebSocket으로 전송
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
                StreamOffset.create(TRADE_UPDATE_STREAM, ReadOffset.lastConsumed()),
                orderUpdateConsumer
        );

        container.start();
        log.info("Trade Update Stream 구독 시작: stream={}, consumer={}", 
                TRADE_UPDATE_STREAM, CONSUMER_TRADE);

        return container;
    }

    // Login Order Sync Stream 구독
    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, LoginOrderSyncEvent>> loginOrderSyncStreamContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(POLL_TIMEOUT)
                .targetType(LoginOrderSyncEvent.class)
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        createConsumerGroup(LOGIN_SYNC_STREAM);
        
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_LOGIN),
                StreamOffset.create(LOGIN_SYNC_STREAM, ReadOffset.from(">")),
                loginOrderSyncConsumer
        );

        container.start();
        log.info("Login Order Sync Stream 구독 시작: stream={}, consumer={}", 
                LOGIN_SYNC_STREAM, CONSUMER_LOGIN);

        return container;
    }

    // Consumer Group 생성 
    private void createConsumerGroup(String stream) {
        try {
            // 기존 Consumer Group 삭제
            try {
                connectionFactory.getConnection().streamCommands()
                        .xGroupDestroy(stream.getBytes(), CONSUMER_GROUP);
                log.info("기존 Consumer Group 삭제: group={}, stream={}", CONSUMER_GROUP, stream);
            } catch (Exception e) {
                log.debug("삭제할 Consumer Group이 없음: group={}, stream={}", CONSUMER_GROUP, stream);
            }
            
            // Consumer Group 생성
            connectionFactory.getConnection().streamCommands()
                    .xGroupCreate(stream.getBytes(), CONSUMER_GROUP, ReadOffset.from("0"), true);
            log.info("Consumer Group 생성: group={}, stream={}", CONSUMER_GROUP, stream);
        } catch (Exception e) {
            log.error("Consumer Group 생성 실패: group={}, stream={}, error={}", 
                    CONSUMER_GROUP, stream, e.getMessage());
        }
    }
}

