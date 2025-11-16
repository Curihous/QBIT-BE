package com.curihous.qbit.api.config;

import com.curihous.qbit.api.consumer.TradeUpdateConsumer;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
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
    private final ObjectMapper objectMapper;

    // Trade Update Stream 구독
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, Object, Object>> tradeUpdateStreamContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(POLL_TIMEOUT)
                .errorHandler(throwable -> {
                    log.error("Trade Update Stream 처리 중 오류 발생: error={}", 
                            throwable.getMessage(), throwable);
                })
                .build();

        @SuppressWarnings("unchecked")
        StreamMessageListenerContainer<String, MapRecord<String, Object, Object>> container = 
                (StreamMessageListenerContainer<String, MapRecord<String, Object, Object>>) 
                (StreamMessageListenerContainer<?, ?>) StreamMessageListenerContainer.create(connectionFactory, options);
        createConsumerGroup(TRADE_UPDATE_STREAM);
        
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_TRADE),
                StreamOffset.create(TRADE_UPDATE_STREAM, ReadOffset.from(">")), // 처리되지 않은 새 메시지만 읽기
                message -> {
                    try {
                        log.info("Trade Update 메시지 수신: messageId={}, stream={}", 
                                message.getId(), message.getStream());
                        
                        // MapRecord에서 value 필드 추출
                        Map<Object, Object> valueMap = message.getValue();
                        Object valueObj = valueMap.get("value");
                        
                        if (valueObj == null) {
                            log.warn("Trade Update 메시지에 value 필드가 없습니다: messageId={}", message.getId());
                            ackMessage(message.getId());
                            return;
                        }
                        
                        // value가 String인 경우 역직렬화, TradeUpdateEvent인 경우 그대로 사용
                        if (valueObj instanceof TradeUpdateEvent event) {
                            log.info("Trade Update 이벤트 수신: userId={}, event={}, symbol={}, orderId={}",
                                    event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
                            tradeUpdateConsumer.onMessage(event);
                            ackMessage(message.getId());
                            log.debug("Trade Update 메시지 처리 완료 및 Ack: messageId={}", message.getId());
                            return;
                        }

                        if (valueObj instanceof String jsonRaw) {
                            String json = jsonRaw.trim();

                            // 배열(JSON Array) 형태로 들어오는 경우: 여러 이벤트를 순차 처리
                            if (json.startsWith("[")) {
                                TradeUpdateEvent[] events = objectMapper.readValue(json, TradeUpdateEvent[].class);
                                if (events == null || events.length == 0) {
                                    log.warn("Trade Update 배열 메시지가 비어 있습니다: messageId={}", message.getId());
                                    ackMessage(message.getId());
                                    return;
                                }

                                for (TradeUpdateEvent e : events) {
                                    if (e == null) continue;
                                    log.info("Trade Update 이벤트 수신(배열): userId={}, event={}, symbol={}, orderId={}",
                                            e.getUserId(), e.getEvent(), e.getSymbol(), e.getAlpacaOrderId());
                                    tradeUpdateConsumer.onMessage(e);
                                }

                                ackMessage(message.getId());
                                log.debug("Trade Update 배열 메시지 처리 완료 및 Ack: messageId={}", message.getId());
                                return;
                            }

                            // 단일 객체 JSON
                            TradeUpdateEvent event = objectMapper.readValue(json, TradeUpdateEvent.class);
                            log.info("Trade Update 이벤트 수신: userId={}, event={}, symbol={}, orderId={}",
                                    event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
                            tradeUpdateConsumer.onMessage(event);
                            ackMessage(message.getId());
                            log.debug("Trade Update 메시지 처리 완료 및 Ack: messageId={}", message.getId());
                            return;
                        }

                        log.warn("Trade Update 메시지의 value가 알 수 없는 타입입니다: messageId={}, type={}",
                                message.getId(), valueObj.getClass().getName());
                        ackMessage(message.getId());
                        return;
                        
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

