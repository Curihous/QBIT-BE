package com.curihous.qbit.realtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.curihous.qbit.realtime.service.AlpacaOrderSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeUpdatesEventHandler {

    private final AlpacaOrderSyncService alpacaOrderSyncService;

    // Trade Update 이벤트 처리
    public void handleTradeUpdate(Long userId, String event, JsonNode data) {
        try {
            log.info("Trade Update 처리 시작: userId={}, event={}", userId, event);
            
            JsonNode orderNode = data.path("order");
            String alpacaOrderId = orderNode.path("id").asText();
            
            switch (event) {
                case "new":
                    handleNewOrder(userId, orderNode);
                    break;
                    
                case "fill":
                    handleFillOrder(userId, data, orderNode);
                    break;
                    
                case "partial_fill":
                    handlePartialFill(userId, data, orderNode);
                    break;
                    
                case "canceled":
                    handleCanceledOrder(userId, orderNode);
                    break;
                    
                case "rejected":
                    handleRejectedOrder(userId, orderNode);
                    break;
                    
                case "replaced":
                    handleReplacedOrder(userId, orderNode);
                    break;
                    
                case "expired":
                    handleExpiredOrder(userId, orderNode);
                    break;
                    
                case "pending_new":
                case "accepted":
                case "pending_cancel":
                case "pending_replace":
                    // 중간 상태들은 로깅만
                    log.info("주문 상태 변경: userId={}, event={}, orderId={}", 
                            userId, event, alpacaOrderId);
                    // 필요시 상태 업데이트
                    alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, event, orderNode);
                    break;
                    
                default:
                    log.warn("알 수 없는 이벤트 타입: event={}, userId={}, orderId={}", 
                            event, userId, alpacaOrderId);
            }
            
        } catch (Exception e) {
            log.error("Trade Update 처리 실패: userId={}, event={}, error={}", 
                    userId, event, e.getMessage(), e);
        }
    }
    
    // 신규 주문 생성 이벤트
    private void handleNewOrder(Long userId, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        log.info("신규 주문 생성: userId={}, orderId={}", userId, alpacaOrderId);
        
        // 주문 상태 업데이트
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "new", orderNode);
    }
    
    // 주문 완전 체결 이벤트
    private void handleFillOrder(Long userId, JsonNode data, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        String symbol = orderNode.path("symbol").asText();
        String side = orderNode.path("side").asText(); // buy or sell
        
        // 체결 정보
        String filledQty = orderNode.path("filled_qty").asText();
        String filledAvgPrice = orderNode.path("filled_avg_price").asText();
        OffsetDateTime filledAt = parseOffsetDateTime(orderNode.path("filled_at").asText());
        
        // fill 이벤트의 추가 정보
        String eventPrice = data.path("price").asText();
        String eventQty = data.path("qty").asText();
        OffsetDateTime eventTimestamp = parseOffsetDateTime(data.path("timestamp").asText());
        String positionQty = data.path("position_qty").asText();
        
        log.info("주문 완전 체결: userId={}, orderId={}, symbol={}, side={}, qty={}, price={}, positionQty={}", 
                userId, alpacaOrderId, symbol, side, eventQty, eventPrice, positionQty);
        
        // 1. 주문 상태 업데이트 (FILLED)
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "filled", orderNode);
        
        // 2. 체결 내역 기록
        alpacaOrderSyncService.recordTradeExecution(
            userId, 
            alpacaOrderId, 
            parseBigDecimal(eventQty),
            parseBigDecimal(eventPrice),
            eventTimestamp
        );
        
        // 3. Portfolio 업데이트
        alpacaOrderSyncService.updatePortfolio(
            userId,
            symbol,
            side,
            parseBigDecimal(eventQty),
            parseBigDecimal(eventPrice)
        );
        
        // 4. TradeCycle 업데이트 (완전 매도 시 사이클 종료)
        alpacaOrderSyncService.updateTradeCycle(
            userId,
            symbol,
            side,
            parseBigDecimal(filledQty),
            parseBigDecimal(filledAvgPrice),
            filledAt
        );
    }
    
    // 주문 부분 체결 이벤트
    private void handlePartialFill(Long userId, JsonNode data, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        String symbol = orderNode.path("symbol").asText();
        String side = orderNode.path("side").asText();
        
        // 부분 체결 정보
        String eventPrice = data.path("price").asText();
        String eventQty = data.path("qty").asText();
        OffsetDateTime eventTimestamp = parseOffsetDateTime(data.path("timestamp").asText());
        String positionQty = data.path("position_qty").asText();
        
        log.info("주문 부분 체결: userId={}, orderId={}, symbol={}, side={}, qty={}, price={}, positionQty={}", 
                userId, alpacaOrderId, symbol, side, eventQty, eventPrice, positionQty);
        
        // 1. 주문 상태 업데이트 (PARTIALLY_FILLED)
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "partially_filled", orderNode);
        
        // 2. 부분 체결 내역 기록
        alpacaOrderSyncService.recordTradeExecution(
            userId,
            alpacaOrderId,
            parseBigDecimal(eventQty),
            parseBigDecimal(eventPrice),
            eventTimestamp
        );
        
        // 3. Portfolio 업데이트 (부분 체결도 즉시 반영)
        alpacaOrderSyncService.updatePortfolio(
            userId,
            symbol,
            side,
            parseBigDecimal(eventQty),
            parseBigDecimal(eventPrice)
        );
    }
    
    // 주문 취소 이벤트
    private void handleCanceledOrder(Long userId, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        log.info("주문 취소: userId={}, orderId={}", userId, alpacaOrderId);
        
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "canceled", orderNode);
    }
    
    // 주문 거부 이벤트
    private void handleRejectedOrder(Long userId, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        log.error("주문 거부: userId={}, orderId={}", userId, alpacaOrderId);
        
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "rejected", orderNode);
    }
    
    // 주문 수정 이벤트
    private void handleReplacedOrder(Long userId, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        String replacedBy = orderNode.path("replaced_by").asText();
        
        log.info("주문 수정됨: userId={}, oldOrderId={}, newOrderId={}", 
                userId, alpacaOrderId, replacedBy);
        
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "replaced", orderNode);
    }
    
    // 주문 만료 이벤트
    private void handleExpiredOrder(Long userId, JsonNode orderNode) {
        String alpacaOrderId = orderNode.path("id").asText();
        log.info("주문 만료: userId={}, orderId={}", userId, alpacaOrderId);
        
        alpacaOrderSyncService.updateOrderStatus(userId, alpacaOrderId, "expired", orderNode);
    }
    
    // BigDecimal 파싱 헬퍼
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("BigDecimal 파싱 실패: value={}", value);
            return BigDecimal.ZERO;
        }
    }
    
    // OffsetDateTime 파싱 헬퍼
    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            log.warn("OffsetDateTime 파싱 실패: value={}", value);
            return null;
        }
    }
}

