package com.curihous.qbit.realtime.config;

import com.curihous.qbit.common.event.TradeUpdateEvent;
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
 * Trade Update 이벤트를 구독하여 WebSocket으로 전송
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamsConfig {

    private static final String STREAM_KEY = "trade-updates";
    private static final String CONSUMER_GROUP = "qbit-realtime-group";
    private static final String CONSUMER_NAME = "qbit-realtime-consumer";

    private final OrderUpdateConsumer orderUpdateConsumer;

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, TradeUpdateEvent>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, TradeUpdateEvent>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(TradeUpdateEvent.class)
                        .build();

        StreamMessageListenerContainer<String, ObjectRecord<String, TradeUpdateEvent>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        // Consumer Group 생성
        try {
            connectionFactory.getConnection().streamCommands()
                    .xGroupCreate(STREAM_KEY.getBytes(), CONSUMER_GROUP, ReadOffset.from("0"), true);
            log.info("Redis Streams Consumer Group 생성: group={}, stream={}", CONSUMER_GROUP, STREAM_KEY);
        } catch (Exception e) {
            log.info("Redis Streams Consumer Group이 이미 존재합니다: group={}", CONSUMER_GROUP);
        }

        // Stream 구독
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                orderUpdateConsumer
        );

        container.start();
        log.info("Redis Streams 구독 시작: stream={}, group={}, consumer={}", 
                STREAM_KEY, CONSUMER_GROUP, CONSUMER_NAME);

        return container;
    }
}

