package com.curihous.qbit.api.config;

import com.curihous.qbit.api.consumer.TradeUpdateConsumer;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

/**
 * Redis Streams 설정
 * Trade Update 이벤트를 Consumer Group으로 구독
 */
@Slf4j
@Configuration
public class RedisStreamsConfig {

    private static final String STREAM_KEY = "trade-updates";
    private static final String CONSUMER_GROUP = "qbit-api-group";
    private static final String CONSUMER_NAME = "qbit-api-consumer";
    
    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, TradeUpdateEvent>> 
            tradeUpdateListenerContainer(RedisConnectionFactory connectionFactory,
                                        TradeUpdateConsumer tradeUpdateConsumer) {
        
        // Consumer Group 생성 (이미 있으면 무시)
        try {
            connectionFactory.getConnection().streamCommands()
                    .xGroupCreate(STREAM_KEY.getBytes(), CONSUMER_GROUP, ReadOffset.from("0-0"), true);
            log.info("Redis Streams Consumer Group 생성: group={}, stream={}", CONSUMER_GROUP, STREAM_KEY);
        } catch (Exception e) {
            log.debug("Consumer Group 이미 존재: group={}", CONSUMER_GROUP);
        }
        
        // StreamMessageListenerContainer 설정
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, TradeUpdateEvent>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(TradeUpdateEvent.class)
                        .build();
        
        StreamMessageListenerContainer<String, ObjectRecord<String, TradeUpdateEvent>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);
        
        // Consumer 등록
        Subscription subscription = container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                tradeUpdateConsumer
        );
        
        container.start();
        
        log.info("Redis Streams Listener 시작: stream={}, group={}, consumer={}", 
                STREAM_KEY, CONSUMER_GROUP, CONSUMER_NAME);
        
        return container;
    }
}

