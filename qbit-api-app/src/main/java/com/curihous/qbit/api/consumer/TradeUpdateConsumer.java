package com.curihous.qbit.api.consumer;

import com.curihous.qbit.api.domain.trade.service.AlpacaOrderSyncService;
import com.curihous.qbit.common.event.TradeUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis Streams Consumer
 * Trade Update 이벤트를 구독하여 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeUpdateConsumer {

    private final AlpacaOrderSyncService alpacaOrderSyncService;
    
    public void onMessage(TradeUpdateEvent event) {
        try {
            log.info("Trade Update Consumer 처리 시작: userId={}, event={}, symbol={}, orderId={}",
                    event.getUserId(), event.getEvent(), event.getSymbol(), event.getAlpacaOrderId());
            
            // 이벤트 처리
            alpacaOrderSyncService.processTradeUpdate(event);
            
            log.debug("Trade Update Consumer 처리 완료: userId={}, event={}", 
                    event.getUserId(), event.getEvent());
            
        } catch (Exception e) {
            log.error("Trade Update 처리 중 오류: userId={}, event={}, error={}", 
                    event != null ? event.getUserId() : "unknown",
                    event != null ? event.getEvent() : "unknown", 
                    e.getMessage(), e);
            throw e; // 상위에서 Ack 처리를 위해
        }
    }
}

