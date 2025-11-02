package com.curihous.qbit.api.consumer;

import com.curihous.qbit.api.domain.trade.service.AlpacaOrderSyncService;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * Redis Streams Consumer
 * Trade Update 이벤트를 구독하여 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeUpdateConsumer implements StreamListener<String, ObjectRecord<String, TradeUpdateEvent>> {

    private final AlpacaOrderSyncService alpacaOrderSyncService;
    
    @Override
    public void onMessage(ObjectRecord<String, TradeUpdateEvent> message) {
        try {
            TradeUpdateEvent event = message.getValue();
            
            log.info("Trade Update 이벤트 수신: userId={}, event={}, symbol={}, orderId={}", 
                    event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
            
            // 이벤트 처리
            alpacaOrderSyncService.processTradeUpdate(event);
            
        } catch (Exception e) {
            log.error("Trade Update 이벤트 처리 실패: messageId={}, error={}", 
                    message.getId(), e.getMessage(), e);
        }
    }
}

