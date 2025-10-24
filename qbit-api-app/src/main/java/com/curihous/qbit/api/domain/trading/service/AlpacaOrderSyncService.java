package com.curihous.qbit.api.domain.trading.service;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.entity.AlpacaConnectionStatus;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.order.repository.OrderRequestRepository;
import com.curihous.qbit.domain.portfolio.entity.Portfolio;
import com.curihous.qbit.domain.portfolio.repository.PortfolioRepository;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.stock.repository.StockRepository;
import com.curihous.qbit.domain.trade.entity.TradeExecution;
import com.curihous.qbit.domain.trade.repository.TradeExecutionRepository;
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.repository.TradeCycleRepository;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import com.curihous.qbit.infra.alpaca.client.AlpacaTradingClient;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Alpaca 주문 동기화 서비스
 * 1. Redis Streams로부터 받은 Trade Update 이벤트를 DB에 반영
 * 2. 로그인 시 Alpaca에서 주문 내역을 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlpacaOrderSyncService {

    private final OrderRequestRepository orderRequestRepository;
    private final TradeExecutionRepository tradeExecutionRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeCycleRepository tradeCycleRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final AlpacaTradingClient alpacaTradingClient;
    private final AlpacaOrderTransactionalService alpacaOrderTransactionalService;

    // ============== 로그인 시 주문 동기화 ==============
    
    // 로그인 시 주문 동기화 이벤트 처리
    @EventListener
    @Async
    public void handleLoginOrderSyncEvent(LoginOrderSyncEvent event) {
        try {
            log.info("로그인 시 주문 동기화 이벤트 처리: userId={}", event.getUserId());
            
            Optional<User> userOpt = userRepository.findById(event.getUserId());
            if (userOpt.isEmpty()) {
                log.warn("사용자를 찾을 수 없음: userId={}", event.getUserId());
                return;
            }
            
            User user = userOpt.get();
            syncOrdersOnLogin(user);
            
        } catch (Exception e) {
            log.error("로그인 시 주문 동기화 이벤트 처리 실패: userId={}, error={}", 
                    event.getUserId(), e.getMessage(), e);
        }
    }

    // 로그인 시 Alpaca 주문 내역 동기화
    @Async
    public void syncOrdersOnLogin(User user) {
        try {
            log.info("로그인 시 주문 동기화 시작: userId={}", user.getId());
            
            // 사용자의 활성화된 Alpaca 연결 조회
            Optional<AlpacaOAuthConnection> connectionOpt = alpacaOAuthConnectionService.findByUserId(user.getId());
            if (connectionOpt.isEmpty()) {
                log.info("Alpaca 연결이 없는 사용자: userId={}", user.getId());
                return;
            }
            
            AlpacaOAuthConnection connection = connectionOpt.get();
            if (!connection.getAlpacaConnectionStatus().equals(AlpacaConnectionStatus.ACTIVE)) {
                log.info("비활성화된 Alpaca 연결: userId={}", user.getId());
                return;
            }
            
            // Alpaca에서 최신 주문 목록 가져오기
            String authorization = "Bearer " + connection.getAccessToken();
            List<AlpacaOrderResponse> alpacaOrders = alpacaTradingClient.getOrders(authorization, "all", 500, "desc", true);
            
            log.info("Alpaca에서 주문 조회 완료: userId={}, 주문 수={}", user.getId(), alpacaOrders.size());
            
            // 각 주문을 DB와 동기화
            int syncedCount = 0;
            for (AlpacaOrderResponse alpacaOrder : alpacaOrders) {
                try {
                    log.info("주문 동기화 시작: alpacaOrderId={}, symbol={}, status={}", 
                            alpacaOrder.id(), alpacaOrder.symbol(), alpacaOrder.status());
                    alpacaOrderTransactionalService.syncSingleOrderFromAlpaca(user, alpacaOrder);
                    syncedCount++;
                    log.info("주문 동기화 완료: alpacaOrderId={}", alpacaOrder.id());
                } catch (Exception e) {
                    log.error("개별 주문 동기화 실패: userId={}, alpacaOrderId={}, error={}", 
                            user.getId(), alpacaOrder.id(), e.getMessage(), e);
                }
            }
            
            log.info("로그인 시 주문 동기화 완료: userId={}, 동기화된 주문 수={}", 
                    user.getId(), syncedCount);
     
        } catch (Exception e) {
            log.error("로그인 시 주문 동기화 실패: userId={}, error={}", 
                    user.getId(), e.getMessage(), e);
        }
    }
    

    // ============== Trade Update 이벤트 처리 ==============

    // Trade Update 이벤트 처리 (진입점)
    @Transactional
    public void processTradeUpdate(TradeUpdateEvent event) {
        log.info("Trade Update 처리: userId={}, event={}, symbol={}", 
                event.getUserId(), event.getEvent(), event.getSymbol());
        
        String eventType = event.getEvent();
        
        switch (eventType) {
            case "fill":
                handleFillEvent(event);
                break;
            case "partial_fill":
                handlePartialFillEvent(event);
                break;
            case "canceled":
            case "rejected":
            case "replaced":
            case "expired":
            case "new":
            case "pending_new":
            case "accepted":
                // 상태만 업데이트
                updateOrderStatus(event);
                break;
            default:
                log.warn("알 수 없는 이벤트: event={}, orderId={}", 
                        eventType, event.getAlpacaOrderId());
        }
    }
    
    // 완전 체결 이벤트 처리
    private void handleFillEvent(TradeUpdateEvent event) {
        // 1. 주문 상태 업데이트
        updateOrderStatus(event);
        
        // 2. 체결 내역 기록
        recordTradeExecution(event);
        
        // 3. Portfolio 업데이트
        updatePortfolio(event);
        
        // 4. TradeCycle 업데이트
        updateTradeCycle(event);
    }
    
    // 부분 체결 이벤트 처리
    private void handlePartialFillEvent(TradeUpdateEvent event) {
        // 1. 주문 상태 업데이트
        updateOrderStatus(event);
        
        // 2. 부분 체결 내역 기록
        recordTradeExecution(event);
        
        // 3. Portfolio 업데이트 (부분 체결도 즉시 반영)
        updatePortfolio(event);
    }
    
    // 주문 상태 업데이트
    private void updateOrderStatus(TradeUpdateEvent event) {
        try {
            Optional<OrderRequest> orderOpt = orderRequestRepository
                    .findByAlpacaOrderId(event.getAlpacaOrderId());
            
            if (orderOpt.isEmpty()) {
                log.warn("주문을 찾을 수 없음: alpacaOrderId={}", event.getAlpacaOrderId());
                return;
            }
            
            OrderRequest order = orderOpt.get();
            
            // 사용자 검증
            if (!order.getUser().getId().equals(event.getUserId())) {
                log.error("사용자 불일치: alpacaOrderId={}, expectedUserId={}, actualUserId={}", 
                        event.getAlpacaOrderId(), event.getUserId(), order.getUser().getId());
                return;
            }
            
            OrderStatus newStatus = convertToOrderStatus(event.getEvent());
            log.info("주문 상태 업데이트: orderId={}, oldStatus={}, newStatus={}", 
                    order.getId(), order.getStatus(), newStatus);
            
            // canceledAt, rejectedAt, expiredAt 타임스탬프 누락 방지를 위해 상태별 switch문 사용
            switch (newStatus) {
                case CANCELED:
                    order.markAsCanceled();
                    break;
                case REJECTED:
                    order.markAsRejected();
                    break;
                case EXPIRED:
                    order.markAsExpired();
                    break;
                default:
                    order.updateStatus(newStatus);
                    break;
            }
            
            // 체결 정보가 있으면 업데이트
            if (event.getFilledQuantity() != null && !event.getFilledQuantity().isEmpty()) {
                BigDecimal filledQty = parseBigDecimal(event.getFilledQuantity());
                BigDecimal filledAvgPrice = parseBigDecimal(event.getFilledAvgPrice());
                OffsetDateTime filledAt = parseOffsetDateTime(event.getFilledAt());
                
                order.updateFilledInfo(filledQty, filledAvgPrice, filledAt);
            }
            
            orderRequestRepository.save(order);
            
        } catch (Exception e) {
            log.error("주문 상태 업데이트 실패: alpacaOrderId={}, error={}", 
                    event.getAlpacaOrderId(), e.getMessage(), e);
        }
    }
    
    // 체결 내역 기록
    private void recordTradeExecution(TradeUpdateEvent event) {
        try {
            Optional<OrderRequest> orderOpt = orderRequestRepository
                    .findByAlpacaOrderId(event.getAlpacaOrderId());
            
            if (orderOpt.isEmpty()) {
                log.warn("주문을 찾을 수 없음: alpacaOrderId={}", event.getAlpacaOrderId());
                return;
            }
            
            OrderRequest order = orderOpt.get();
            User user = order.getUser();
            
            BigDecimal quantity = parseBigDecimal(event.getEventQuantity());
            BigDecimal price = parseBigDecimal(event.getEventPrice());
            OffsetDateTime executedAt = parseOffsetDateTime(event.getEventTimestamp());
            
            LocalDateTime executedAtLocal = executedAt != null 
                    ? executedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
            
            TradeExecution execution = new TradeExecution(
                    quantity,
                    price,
                    executedAtLocal,
                    order,
                    user
            );
            
            tradeExecutionRepository.save(execution);
            
            log.info("체결 내역 기록: orderId={}, qty={}, price={}", 
                    order.getId(), quantity, price);
            
        } catch (Exception e) {
            log.error("체결 내역 기록 실패: alpacaOrderId={}, error={}", 
                    event.getAlpacaOrderId(), e.getMessage(), e);
        }
    }
    
    // Portfolio 업데이트
    private void updatePortfolio(TradeUpdateEvent event) {
        try {
            Optional<User> userOpt = userRepository.findById(event.getUserId());
            if (userOpt.isEmpty()) {
                log.error("사용자를 찾을 수 없음: userId={}", event.getUserId());
                return;
            }
            
            Optional<Stock> stockOpt = stockRepository.findBySymbol(event.getSymbol());
            if (stockOpt.isEmpty()) {
                log.error("종목을 찾을 수 없음: symbol={}", event.getSymbol());
                return;
            }
            
            User user = userOpt.get();
            Stock stock = stockOpt.get();
            
            BigDecimal quantity = parseBigDecimal(event.getEventQuantity());
            BigDecimal price = parseBigDecimal(event.getEventPrice());
            
            Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserAndStock(user, stock);
            
            if ("buy".equalsIgnoreCase(event.getSide())) {
                handleBuy(portfolioOpt, user, stock, quantity, price);
            } else if ("sell".equalsIgnoreCase(event.getSide())) {
                handleSell(portfolioOpt, user, stock, quantity, price);
            }
            
        } catch (Exception e) {
            log.error("Portfolio 업데이트 실패: userId={}, symbol={}, error={}", 
                    event.getUserId(), event.getSymbol(), e.getMessage(), e);
        }
    }
    
    // TradeCycle 업데이트
    private void updateTradeCycle(TradeUpdateEvent event) {
        try {
            Optional<User> userOpt = userRepository.findById(event.getUserId());
            if (userOpt.isEmpty()) {
                log.error("사용자를 찾을 수 없음: userId={}", event.getUserId());
                return;
            }
            
            Optional<Stock> stockOpt = stockRepository.findBySymbol(event.getSymbol());
            if (stockOpt.isEmpty()) {
                log.error("종목을 찾을 수 없음: symbol={}", event.getSymbol());
                return;
            }
            
            User user = userOpt.get();
            Stock stock = stockOpt.get();
            
            BigDecimal filledQty = parseBigDecimal(event.getFilledQuantity());
            BigDecimal filledAvgPrice = parseBigDecimal(event.getFilledAvgPrice());
            OffsetDateTime filledAt = parseOffsetDateTime(event.getFilledAt());
            
            if ("buy".equalsIgnoreCase(event.getSide())) {
                handleBuyTradeCycle(user, stock, filledQty, filledAvgPrice, filledAt);
            } else if ("sell".equalsIgnoreCase(event.getSide())) {
                handleSellTradeCycle(user, stock, filledQty, filledAvgPrice, filledAt);
            }
            
        } catch (Exception e) {
            log.error("TradeCycle 업데이트 실패: userId={}, symbol={}, error={}", 
                    event.getUserId(), event.getSymbol(), e.getMessage(), e);
        }
    }
    
    // === 헬퍼 메서드 ===
    
    private void handleBuy(Optional<Portfolio> portfolioOpt, User user, Stock stock, 
                          BigDecimal quantity, BigDecimal price) {
        if (portfolioOpt.isPresent()) {
            Portfolio portfolio = portfolioOpt.get();
            
            BigDecimal currentQty = portfolio.getQuantity();
            BigDecimal currentAvgPrice = portfolio.getAveragePurchasePrice();
            
            BigDecimal currentTotalCost = currentQty.multiply(currentAvgPrice);
            BigDecimal newTotalCost = quantity.multiply(price);
            BigDecimal totalCost = currentTotalCost.add(newTotalCost);
            
            BigDecimal newTotalQty = currentQty.add(quantity);
            BigDecimal newAvgPrice = totalCost.divide(newTotalQty, 8, RoundingMode.HALF_UP);

            portfolio.updateQuantityAndPrice(newTotalQty, newAvgPrice);
            portfolioRepository.save(portfolio);
            
            log.info("매수 - Portfolio 업데이트: userId={}, symbol={}, qty={}→{}, avgPrice={}→{}", 
                    user.getId(), stock.getSymbol(), currentQty, newTotalQty, currentAvgPrice, newAvgPrice);
        } else {
            Portfolio newPortfolio = new Portfolio(quantity, price, user, stock);
            portfolioRepository.save(newPortfolio);
            
            log.info("매수 - Portfolio 신규 생성: userId={}, symbol={}, qty={}, avgPrice={}", 
                    user.getId(), stock.getSymbol(), quantity, price);
        }
    }
    
    private void handleSell(Optional<Portfolio> portfolioOpt, User user, Stock stock, 
                           BigDecimal quantity, BigDecimal price) {
        if (portfolioOpt.isEmpty()) {
            log.warn("매도할 포지션이 없음: userId={}, symbol={}", user.getId(), stock.getSymbol());
            return;
        }
        
        Portfolio portfolio = portfolioOpt.get();
        BigDecimal currentQty = portfolio.getQuantity();
        BigDecimal remainingQty = currentQty.subtract(quantity);
        
        if (remainingQty.compareTo(BigDecimal.ZERO) < 0) {
            log.error("매도 수량 초과: userId={}, symbol={}, currentQty={}, sellQty={}", 
                    user.getId(), stock.getSymbol(), currentQty, quantity);
            return;
        }
        
        if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
            portfolioRepository.delete(portfolio);
            log.info("매도 - Portfolio 삭제 (전량 매도): userId={}, symbol={}, qty={}", 
                    user.getId(), stock.getSymbol(), currentQty);
        } else {
            portfolio.updateQuantity(remainingQty);
            portfolioRepository.save(portfolio);
            log.info("매도 - Portfolio 업데이트: userId={}, symbol={}, qty={}→{}", 
                    user.getId(), stock.getSymbol(), currentQty, remainingQty);
        }
    }
    
    private void handleBuyTradeCycle(User user, Stock stock, BigDecimal qty, 
                                     BigDecimal avgPrice, OffsetDateTime filledAt) {
        Optional<TradeCycle> activeCycleOpt = tradeCycleRepository
                .findByUserAndStockAndEndDateIsNull(user, stock);
        
        LocalDateTime filledAtLocal = filledAt != null 
                ? filledAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now();
        
        if (activeCycleOpt.isEmpty()) {
            TradeCycle newCycle = new TradeCycle(
                    filledAtLocal,
                    null,
                    BigDecimal.ZERO,
                    qty.multiply(avgPrice),
                    avgPrice,
                    null,
                    qty.multiply(avgPrice),
                    BigDecimal.ZERO,
                    user,
                    stock
            );
            tradeCycleRepository.save(newCycle);
            log.info("TradeCycle 신규 생성: userId={}, symbol={}, startDate={}", 
                    user.getId(), stock.getSymbol(), filledAtLocal);
        } else {
            TradeCycle cycle = activeCycleOpt.get();
            cycle.updateOnAdditionalBuy(qty, avgPrice);
            tradeCycleRepository.save(cycle);
            log.info("TradeCycle 업데이트 (추가 매수): userId={}, symbol={}, cycleId={}", 
                    user.getId(), stock.getSymbol(), cycle.getId());
        }
    }
    
    private void handleSellTradeCycle(User user, Stock stock, BigDecimal qty, 
                                      BigDecimal avgPrice, OffsetDateTime filledAt) {
        Optional<TradeCycle> activeCycleOpt = tradeCycleRepository
                .findByUserAndStockAndEndDateIsNull(user, stock);
        
        if (activeCycleOpt.isEmpty()) {
            log.warn("진행 중인 TradeCycle이 없음: userId={}, symbol={}", user.getId(), stock.getSymbol());
            return;
        }
        
        TradeCycle cycle = activeCycleOpt.get();
        
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserAndStock(user, stock);
        boolean isFullSell = portfolioOpt.isEmpty() || 
            portfolioOpt.get().getQuantity().compareTo(BigDecimal.ZERO) == 0;
        
        if (isFullSell) {
            LocalDateTime filledAtLocal = filledAt != null 
                    ? filledAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
            
            cycle.close(filledAtLocal, qty, avgPrice);
            tradeCycleRepository.save(cycle);
            
            log.info("TradeCycle 종료 (전량 매도): userId={}, symbol={}, cycleId={}, profitLossRate={}%", 
                    user.getId(), stock.getSymbol(), cycle.getId(), cycle.getProfitLossRate());
        } else {
            cycle.updateOnPartialSell(qty, avgPrice);
            tradeCycleRepository.save(cycle);
            log.info("TradeCycle 업데이트 (부분 매도): userId={}, symbol={}, cycleId={}", 
                    user.getId(), stock.getSymbol(), cycle.getId());
        }
    }
    
    private OrderStatus convertToOrderStatus(String event) {
        return switch (event.toLowerCase()) {
            case "new" -> OrderStatus.NEW;
            case "partially_filled", "partial_fill" -> OrderStatus.PARTIALLY_FILLED;
            case "filled", "fill" -> OrderStatus.FILLED;
            case "canceled" -> OrderStatus.CANCELED;
            case "rejected" -> OrderStatus.REJECTED;
            case "replaced" -> OrderStatus.REPLACED;
            case "expired" -> OrderStatus.EXPIRED;
            case "pending_new" -> OrderStatus.PENDING_NEW;
            case "pending_cancel" -> OrderStatus.PENDING_CANCEL;
            case "pending_replace" -> OrderStatus.PENDING_REPLACE;
            default -> {
                log.warn("알 수 없는 이벤트: {}", event);
                yield OrderStatus.NEW;
            }
        };
    }
    
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

