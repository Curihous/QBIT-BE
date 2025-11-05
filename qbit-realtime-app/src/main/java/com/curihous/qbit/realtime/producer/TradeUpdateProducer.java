package com.curihous.qbit.realtime.producer;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Streams Producer
 * Alpaca Trade Update 이벤트를 Redis Streams에 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeUpdateProducer {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String STREAM_KEY = "trade-updates";
    
    // Trade Update 이벤트 발행
    public void publishTradeUpdate(TradeUpdateEvent event) {
        try {
            ObjectRecord<String, TradeUpdateEvent> record = ObjectRecord.create(STREAM_KEY, event);
            
            redisTemplate.opsForStream().add(record);
            
            log.info("Trade Update 이벤트 발행: userId={}, event={}, symbol={}, orderId={}", 
                    event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
            
        } catch (Exception e) {
            log.error("Trade Update 이벤트 발행 실패: event={}, error={}", 
                    event.getEvent(), e.getMessage(), e);
        }
    }
}

