package com.curihous.qbit.api.config;

import com.curihous.qbit.api.consumer.TradeUpdateConsumer;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.Map;

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

    // ObjectMapper 생성 
    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        return objectMapper;
    }

    // Trade Update Stream 구독
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> tradeUpdateStreamContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(POLL_TIMEOUT)
                .errorHandler(throwable -> {
                    log.error("Trade Update Stream 처리 중 오류 발생: error={}", 
                            throwable.getMessage(), throwable);
                })
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);
        createConsumerGroup(TRADE_UPDATE_STREAM);
        
        ObjectMapper objectMapper = createObjectMapper();
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_TRADE),
                StreamOffset.create(TRADE_UPDATE_STREAM, ReadOffset.lastConsumed()),
                message -> {
                    try {
                        log.info("Trade Update 메시지 수신: messageId={}, stream={}", 
                                message.getId(), message.getStream());
                        
                        Map<String, String> valueMap = message.getValue();
                        
                        String serializedValue = null;
                        for (String key : valueMap.keySet()) {
                            String value = valueMap.get(key);
                            if (value != null && value.startsWith("{")) {
                                serializedValue = value;
                                break;
                            }
                        }
                        
                        if (serializedValue == null) {
                            log.warn("Trade Update 메시지에서 직렬화된 값을 찾을 수 없습니다: messageId={}", 
                                    message.getId());
                            ackMessage(message.getId());
                            return;
                        }
                        
                        // GenericJackson2JsonRedisSerializer로 역직렬화
                        byte[] bytes = serializedValue.getBytes();
                        Object deserialized = serializer.deserialize(bytes);
                        
                        if (!(deserialized instanceof TradeUpdateEvent)) {
                            log.warn("Trade Update 메시지 역직렬화 결과가 TradeUpdateEvent가 아닙니다: messageId={}, type={}", 
                                    message.getId(), deserialized != null ? deserialized.getClass() : "null");
                            ackMessage(message.getId());
                            return;
                        }
                        
                        TradeUpdateEvent event = (TradeUpdateEvent) deserialized;
                        
                        log.info("Trade Update 이벤트 수신: userId={}, event={}, symbol={}, orderId={}", 
                                event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
                        
                        // 이벤트 처리
                        tradeUpdateConsumer.onMessage(event);
                        
                        // 메시지 정상 처리 후 Ack
                        ackMessage(message.getId());
                        
                        log.debug("Trade Update 메시지 처리 완료 및 Ack: messageId={}", message.getId());
                        
                    } catch (Exception e) {
                        log.error("Trade Update 이벤트 처리 실패: messageId={}, error={}", 
                                message != null ? message.getId() : "unknown", e.getMessage(), e);
                        // 오류 발생 시에도 Ack 처리 (무한 재시도 방지)
                        if (message != null) {
                            ackMessage(message.getId());
                        }
                    }
                }
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
    
    // 메시지 Ack 처리
    private void ackMessage(org.springframework.data.redis.connection.stream.RecordId messageId) {
        try {
            connectionFactory.getConnection().streamCommands()
                    .xAck(TRADE_UPDATE_STREAM.getBytes(), CONSUMER_GROUP, messageId.getValue());
            log.debug("Trade Update 메시지 Ack 처리: messageId={}", messageId);
        } catch (Exception e) {
            log.error("Trade Update 메시지 Ack 처리 실패: messageId={}, error={}", 
                    messageId, e.getMessage(), e);
        }
    }
}

