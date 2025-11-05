package com.curihous.qbit.api.config;

import com.curihous.qbit.api.consumer.TradeUpdateConsumer;
import com.curihous.qbit.common.event.TradeUpdateEvent;
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
 * Trade Update 이벤트를 구독하여 DB에 반영
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamsConfig {

    // Stream 키
    private static final String TRADE_UPDATE_STREAM = "trade-updates";
    
    // Consumer 그룹 및 이름
    private static final String CONSUMER_GROUP = "qbit-api-group";
    private static final String CONSUMER_TRADE = "qbit-api-consumer-trade";
    
    // Polling 설정
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(100);

    private final RedisConnectionFactory connectionFactory;
    private final TradeUpdateConsumer tradeUpdateConsumer;

    // Trade Update Stream 구독
    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, TradeUpdateEvent>> tradeUpdateStreamContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(POLL_TIMEOUT)
                .targetType(TradeUpdateEvent.class)
                .errorHandler(throwable -> {
                    log.error("Trade Update Stream 처리 중 오류 발생: error={}", 
                            throwable.getMessage(), throwable);
                })
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        createConsumerGroup(TRADE_UPDATE_STREAM);
        
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_TRADE),
                StreamOffset.create(TRADE_UPDATE_STREAM, ReadOffset.from("0")),
                tradeUpdateConsumer
        );

        container.start();
        log.info("Trade Update Stream 구독 시작: stream={}, consumer={}, group={}", 
                TRADE_UPDATE_STREAM, CONSUMER_TRADE, CONSUMER_GROUP);

        return container;
    }

    // Consumer Group 생성 (없는 경우에만)
    private void createConsumerGroup(String stream) {
        try {
            connectionFactory.getConnection().streamCommands()
                    .xGroupCreate(stream.getBytes(), CONSUMER_GROUP, ReadOffset.from("0"), true);
            log.info("Consumer Group 생성: group={}, stream={}", CONSUMER_GROUP, stream);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("BUSYGROUP")) {
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

