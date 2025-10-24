package com.curihous.qbit.api.domain.trading.service;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Alpaca 주문 동기화 트랜잭션 서비스
 * @Async 메서드에서 호출되는 트랜잭션 경계가 필요한 작업들을 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlpacaOrderTransactionalService {

    private final OrderRequestRepository orderRequestRepository;
    private final StockRepository stockRepository;

    @Transactional
    public void syncSingleOrderFromAlpaca(User user, AlpacaOrderResponse alpacaOrder) {
        log.info("syncSingleOrderFromAlpaca 시작: userId={}, alpacaOrderId={}", user.getId(), alpacaOrder.id());
        
        // 기존 주문이 있는지 확인
        Optional<OrderRequest> existingOrderOpt = orderRequestRepository
                .findByAlpacaOrderId(alpacaOrder.id());
        
        if (existingOrderOpt.isPresent()) {
            // 기존 주문 업데이트
            OrderRequest existingOrder = existingOrderOpt.get();
            
            // 사용자 검증
            if (!existingOrder.getUser().getId().equals(user.getId())) {
                log.warn("주문 소유자 불일치: alpacaOrderId={}, expectedUserId={}, actualUserId={}", 
                        alpacaOrder.id(), user.getId(), existingOrder.getUser().getId());
                return;
            }
            
            updateExistingOrderFromAlpaca(existingOrder, alpacaOrder);
            log.info("기존 주문 업데이트: orderId={}, alpacaOrderId={}, status={}", 
                    existingOrder.getId(), alpacaOrder.id(), alpacaOrder.status());
        } else {
            // 새 주문 생성
            createNewOrderFromAlpaca(user, alpacaOrder);
            log.info("새 주문 생성: alpacaOrderId={}, symbol={}, status={}", 
                    alpacaOrder.id(), alpacaOrder.symbol(), alpacaOrder.status());
        }
    }

    // 기존 주문을 Alpaca 데이터로 업데이트
    private void updateExistingOrderFromAlpaca(OrderRequest existingOrder, AlpacaOrderResponse alpacaOrder) {
        OrderStatus newStatus = convertToOrderStatus(alpacaOrder.status());

        switch (newStatus) {
            case CANCELED:
                if (alpacaOrder.canceledAt() != null) {
                    existingOrder.markAsCanceled();
                } else {
                    existingOrder.updateStatus(newStatus);
                }
                break;
            case FILLED:
                if (alpacaOrder.filledAt() != null) {
                    existingOrder.updateStatus(newStatus);
                    updateFilledInfoFromAlpaca(existingOrder, alpacaOrder);
                } else {
                    existingOrder.updateStatus(newStatus);
                }
                break;
            default:
                existingOrder.updateStatus(newStatus);
                break;
        }
        
        orderRequestRepository.save(existingOrder);
    }

    // Alpaca 데이터로 새 주문 생성
    private void createNewOrderFromAlpaca(User user, AlpacaOrderResponse alpacaOrder) {
        log.info("createNewOrderFromAlpaca 시작: userId={}, alpacaOrderId={}", user.getId(), alpacaOrder.id());
        
        // 주식 정보 조회 또는 생성
        Stock stock = getOrCreateStock(alpacaOrder.symbol());
        log.info("주식 정보 조회/생성 완료: stockId={}, symbol={}", stock.getId(), stock.getSymbol());
        
        OrderRequest newOrder = OrderRequest.builder()
                .user(user)
                .stock(stock)
                .alpacaOrderId(alpacaOrder.id())
                .clientOrderId(alpacaOrder.clientOrderId())
                .side(convertToOrderSide(alpacaOrder.side()))
                .type(convertToOrderType(alpacaOrder.type()))
                .timeInForce(convertToTimeInForce(alpacaOrder.timeInForce()))
                .quantity(parseBigDecimal(alpacaOrder.quantity()))
                .status(convertToOrderStatus(alpacaOrder.status()))
                .alpacaCreatedAt(alpacaOrder.createdAt())
                .submittedAt(alpacaOrder.submittedAt())
                .filledAt(alpacaOrder.filledAt())
                .canceledAt(alpacaOrder.canceledAt())
                .build();
        
        // 체결 정보 설정
        if (alpacaOrder.filledQuantity() != null && !alpacaOrder.filledQuantity().isEmpty()) {
            BigDecimal filledQty = parseBigDecimal(alpacaOrder.filledQuantity());
            BigDecimal filledAvgPrice = parseBigDecimal(alpacaOrder.filledAvgPrice());
            newOrder.updateFilledInfo(filledQty, filledAvgPrice, alpacaOrder.filledAt());
        }
        
        log.info("DB 저장 전: alpacaOrderId={}, symbol={}, status={}, quantity={}", 
                alpacaOrder.id(), alpacaOrder.symbol(), alpacaOrder.status(), alpacaOrder.quantity());
        
        try {
            OrderRequest savedOrder = orderRequestRepository.save(newOrder);
            log.info("DB 저장 완료: orderId={}, alpacaOrderId={}", 
                    savedOrder.getId(), savedOrder.getAlpacaOrderId());
        } catch (Exception e) {
            log.error("DB 저장 실패: alpacaOrderId={}, error={}", 
                    alpacaOrder.id(), e.getMessage(), e);
            throw e;
        }
    }

    // Alpaca 데이터로 체결 정보 업데이트
    private void updateFilledInfoFromAlpaca(OrderRequest order, AlpacaOrderResponse alpacaOrder) {
        if (alpacaOrder.filledQuantity() != null && !alpacaOrder.filledQuantity().isEmpty()) {
            BigDecimal filledQty = parseBigDecimal(alpacaOrder.filledQuantity());
            BigDecimal filledAvgPrice = parseBigDecimal(alpacaOrder.filledAvgPrice());
            order.updateFilledInfo(filledQty, filledAvgPrice, alpacaOrder.filledAt());
        }
    }

    // 주식 정보 조회 또는 생성
    private Stock getOrCreateStock(String symbol) {
        return stockRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    Stock newStock = Stock.builder()
                            .symbol(symbol)
                            .build();
                    return stockRepository.save(newStock);
                });
    }

    // === 유틸리티 메서드들 === // 
    
    private com.curihous.qbit.domain.order.entity.OrderSide convertToOrderSide(String side) {
        return switch (side.toLowerCase()) {
            case "buy" -> com.curihous.qbit.domain.order.entity.OrderSide.BUY;
            case "sell" -> com.curihous.qbit.domain.order.entity.OrderSide.SELL;
            default -> throw new IllegalArgumentException("Unknown order side: " + side);
        };
    }

    private com.curihous.qbit.domain.order.entity.OrderType convertToOrderType(String type) {
        return switch (type.toLowerCase()) {
            case "market" -> com.curihous.qbit.domain.order.entity.OrderType.MARKET;
            case "limit" -> com.curihous.qbit.domain.order.entity.OrderType.LIMIT;
            case "stop" -> com.curihous.qbit.domain.order.entity.OrderType.STOP;
            case "stop_limit" -> com.curihous.qbit.domain.order.entity.OrderType.STOP_LIMIT;
            default -> throw new IllegalArgumentException("Unknown order type: " + type);
        };
    }

    private com.curihous.qbit.domain.order.entity.TimeInForce convertToTimeInForce(String timeInForce) {
        return switch (timeInForce.toLowerCase()) {
            case "day" -> com.curihous.qbit.domain.order.entity.TimeInForce.DAY;
            case "gtc" -> com.curihous.qbit.domain.order.entity.TimeInForce.GTC;
            case "ioc" -> com.curihous.qbit.domain.order.entity.TimeInForce.IOC;
            case "fok" -> com.curihous.qbit.domain.order.entity.TimeInForce.FOK;
            default -> throw new IllegalArgumentException("Unknown time in force: " + timeInForce);
        };
    }

    private OrderStatus convertToOrderStatus(String status) {
        return switch (status.toLowerCase()) {
            case "new" -> OrderStatus.NEW;
            case "pending_new" -> OrderStatus.PENDING_NEW;
            case "accepted" -> OrderStatus.ACCEPTED;
            case "partially_filled" -> OrderStatus.PARTIALLY_FILLED;
            case "filled" -> OrderStatus.FILLED;
            case "done_for_day" -> OrderStatus.DONE_FOR_DAY;
            case "canceled" -> OrderStatus.CANCELED;
            case "expired" -> OrderStatus.EXPIRED;
            case "replaced" -> OrderStatus.REPLACED;
            case "pending_cancel" -> OrderStatus.PENDING_CANCEL;
            case "pending_replace" -> OrderStatus.PENDING_REPLACE;
            case "rejected" -> OrderStatus.REJECTED;
            case "suspended" -> OrderStatus.SUSPENDED;
            case "calculated" -> OrderStatus.CALCULATED;
            default -> throw new IllegalArgumentException("Unknown order status: " + status);
        };
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("BigDecimal 파싱 실패: value={}", value);
            return BigDecimal.ZERO;
        }
    }
}
