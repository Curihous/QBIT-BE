package com.curihous.qbit.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 주문 동기화 서비스
 * Alpaca WebSocket으로부터 받은 주문 업데이트를 DB에 반영
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

    /* 주문 상태 업데이트 */
    @Transactional
    public void updateOrderStatus(Long userId, String alpacaOrderId, String event, JsonNode orderNode) {
        try {
            Optional<OrderRequest> orderOptional = orderRequestRepository
                    .findByAlpacaOrderId(alpacaOrderId);
            
            if (orderOptional.isEmpty()) {
                log.warn("주문을 찾을 수 없음: alpacaOrderId={}, userId={}", alpacaOrderId, userId);
                return;
            }
            
            OrderRequest order = orderOptional.get();
            
            // 사용자 검증
            if (!order.getUser().getId().equals(userId)) {
                log.error("사용자 불일치: alpacaOrderId={}, expectedUserId={}, actualUserId={}", 
                        alpacaOrderId, userId, order.getUser().getId());
                return;
            }
            
            // 상태 업데이트 
            OrderStatus newStatus = convertToOrderStatus(event);
            log.info("주문 상태 업데이트: orderId={}, oldStatus={}, newStatus={}", 
                    order.getId(), order.getStatus(), newStatus);
            
            // filled_quantity, filled_avg_price
            updateOrderFields(order, orderNode);
            
            orderRequestRepository.save(order);
            
        } catch (Exception e) {
            log.error("주문 상태 업데이트 실패: alpacaOrderId={}, event={}, error={}", 
                    alpacaOrderId, event, e.getMessage(), e);
        }
    }
    
    /* 체결 내역(TradeExecution) 기록 */
    @Transactional
    public void recordTradeExecution(Long userId, String alpacaOrderId, 
                                     BigDecimal quantity, BigDecimal price, 
                                     OffsetDateTime executedAt) {
        try {
            Optional<OrderRequest> orderOptional = orderRequestRepository
                    .findByAlpacaOrderId(alpacaOrderId);
            
            if (orderOptional.isEmpty()) {
                log.warn("주문을 찾을 수 없음: alpacaOrderId={}", alpacaOrderId);
                return;
            }
            
            OrderRequest order = orderOptional.get();
            User user = order.getUser();
            
            LocalDateTime executedAtLocal = executedAt != null 
                    ? executedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
            
            TradeExecution execution = new TradeExecution(
                    quantity.intValue(),
                    price,
                    executedAtLocal,
                    order,
                    user
            );
            
            tradeExecutionRepository.save(execution);
            
            log.info("체결 내역 기록: orderId={}, qty={}, price={}, executedAt={}", 
                    order.getId(), quantity, price, executedAtLocal);
            
        } catch (Exception e) {
            log.error("체결 내역 기록 실패: alpacaOrderId={}, error={}", 
                    alpacaOrderId, e.getMessage(), e);
        }
    }
    
    /* Portfolio 업데이트 */
    @Transactional
    public void updatePortfolio(Long userId, String symbol, String side, 
                                BigDecimal quantity, BigDecimal price) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.error("사용자를 찾을 수 없음: userId={}", userId);
                return;
            }
            
            Optional<Stock> stockOpt = stockRepository.findBySymbol(symbol);
            if (stockOpt.isEmpty()) {
                log.error("종목을 찾을 수 없음: userId={}, symbol={}", userId, symbol);
                return;
            }
            
            User user = userOpt.get();
            Stock stock = stockOpt.get();
            
            Optional<Portfolio> portfolioOptional = portfolioRepository
                    .findByUserAndStock(user, stock);
            
            if ("buy".equalsIgnoreCase(side)) {
                handleBuy(portfolioOptional, user, stock, quantity, price);
            } else if ("sell".equalsIgnoreCase(side)) {
                handleSell(portfolioOptional, user, stock, quantity, price);
            }
            
        } catch (Exception e) {
            log.error("Portfolio 업데이트 실패: userId={}, symbol={}, side={}, error={}", 
                    userId, symbol, side, e.getMessage(), e);
        }
    }
    
    // 매수 처리
    private void handleBuy(Optional<Portfolio> portfolioOptional, User user, Stock stock, 
                          BigDecimal quantity, BigDecimal price) {
        if (portfolioOptional.isPresent()) {
            // 기존 포지션 있음 -> 평균 단가 재계산
            Portfolio portfolio = portfolioOptional.get();
            
            BigDecimal currentQty = new BigDecimal(portfolio.getQuantity());
            BigDecimal currentAvgPrice = portfolio.getAveragePurchasePrice();
            
            // 총 매수 금액
            BigDecimal currentTotalCost = currentQty.multiply(currentAvgPrice);
            BigDecimal newTotalCost = quantity.multiply(price);
            BigDecimal totalCost = currentTotalCost.add(newTotalCost);
            
            // 새로운 평균 단가
            BigDecimal newTotalQty = currentQty.add(quantity);
            BigDecimal newAvgPrice = totalCost.divide(newTotalQty, 8, RoundingMode.HALF_UP);

            portfolio.updateQuantityAndPrice(newTotalQty.intValue(), newAvgPrice);
            portfolioRepository.save(portfolio);
            
            log.info("매수 - Portfolio 업데이트: userId={}, symbol={}, qty={}→{}, avgPrice={}→{}", 
                    user.getId(), stock.getSymbol(), currentQty, newTotalQty, currentAvgPrice, newAvgPrice);
            
        } else {
            // 신규 포지션
            Portfolio newPortfolio = new Portfolio(
                    quantity.intValue(),
                    price,
                    user,
                    stock
            );
            portfolioRepository.save(newPortfolio);
            
            log.info("매수 - Portfolio 신규 생성: userId={}, symbol={}, qty={}, avgPrice={}", 
                    user.getId(), stock.getSymbol(), quantity, price);
        }
    }
    
    // 매도 처리
    private void handleSell(Optional<Portfolio> portfolioOptional, User user, Stock stock, 
                           BigDecimal quantity, BigDecimal price) {
        if (portfolioOptional.isEmpty()) {
            log.warn("매도할 포지션이 없음: userId={}, symbol={}", user.getId(), stock.getSymbol());
            return;
        }
        
        Portfolio portfolio = portfolioOptional.get();
        BigDecimal currentQty = new BigDecimal(portfolio.getQuantity());
        BigDecimal remainingQty = currentQty.subtract(quantity);
        
        if (remainingQty.compareTo(BigDecimal.ZERO) < 0) {
            log.error("매도 수량 초과: userId={}, symbol={}, currentQty={}, sellQty={}", 
                    user.getId(), stock.getSymbol(), currentQty, quantity);
            return;
        }
        
        if (remainingQty.compareTo(BigDecimal.ZERO) == 0) {
            // 전량 매도 -> Portfolio 삭제
            portfolioRepository.delete(portfolio);
            log.info("매도 - Portfolio 삭제 (전량 매도): userId={}, symbol={}, qty={}", 
                    user.getId(), stock.getSymbol(), currentQty);
        } else {
            // 부분 매도 -> 수량만 감소 (평균 단가는 유지)
            portfolio.updateQuantity(remainingQty.intValue());
            portfolioRepository.save(portfolio);
            log.info("매도 - Portfolio 업데이트: userId={}, symbol={}, qty={}→{}", 
                    user.getId(), stock.getSymbol(), currentQty, remainingQty);
        }
    }
    
    /* TradeCycle 업데이트 */
    @Transactional
    public void updateTradeCycle(Long userId, String symbol, String side, 
                                 BigDecimal totalFilledQty, BigDecimal avgPrice, 
                                 OffsetDateTime filledAt) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.error("사용자를 찾을 수 없음: userId={}", userId);
                return;
            }
            
            Optional<Stock> stockOpt = stockRepository.findBySymbol(symbol);
            if (stockOpt.isEmpty()) {
                log.error("종목을 찾을 수 없음: userId={}, symbol={}", userId, symbol);
                return;
            }
            
            User user = userOpt.get();
            Stock stock = stockOpt.get();
            
            if ("buy".equalsIgnoreCase(side)) {
                handleBuyTradeCycle(user, stock, totalFilledQty, avgPrice, filledAt);
            } else if ("sell".equalsIgnoreCase(side)) {
                handleSellTradeCycle(user, stock, totalFilledQty, avgPrice, filledAt);
            }
            
        } catch (Exception e) {
            log.error("TradeCycle 업데이트 실패: userId={}, symbol={}, side={}, error={}", 
                    userId, symbol, side, e.getMessage(), e);
        }
    }
    
    // 매수 시 TradeCycle 생성 or 업데이트
    private void handleBuyTradeCycle(User user, Stock stock, BigDecimal qty, 
                                     BigDecimal avgPrice, OffsetDateTime filledAt) {
        // 진행 중인 사이클 찾기 (endDate가 null인 것)
        Optional<TradeCycle> activeCycleOpt = tradeCycleRepository
                .findByUserAndStockAndEndDateIsNull(user, stock);
        
        LocalDateTime filledAtLocal = filledAt != null 
                ? filledAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now();
        
        if (activeCycleOpt.isEmpty()) {
            // 신규 사이클 생성
            TradeCycle newCycle = new TradeCycle(
                    filledAtLocal, // startDate
                    null, // endDate
                    BigDecimal.ZERO, // profitLossRate
                    qty.multiply(avgPrice), // totalInvestmentAmount
                    avgPrice, // averageBuyPrice
                    null, // averageSellPrice
                    qty.multiply(avgPrice), // peakInvestment
                    BigDecimal.ZERO, // maxDrawdown
                    user,
                    stock
            );
            tradeCycleRepository.save(newCycle);
            log.info("TradeCycle 신규 생성: userId={}, symbol={}, startDate={}", 
                    user.getId(), stock.getSymbol(), filledAtLocal);
        } else {
            // 기존 사이클 업데이트 (추가 매수)
            TradeCycle cycle = activeCycleOpt.get();
            cycle.updateOnAdditionalBuy(qty, avgPrice);
            tradeCycleRepository.save(cycle);
            log.info("TradeCycle 업데이트 (추가 매수): userId={}, symbol={}, cycleId={}, qty={}, avgBuyPrice={}", 
                    user.getId(), stock.getSymbol(), cycle.getId(), qty, cycle.getAverageBuyPrice());
        }
    }
    
    // 매도 시 TradeCycle 업데이트 or 종료
    private void handleSellTradeCycle(User user, Stock stock, BigDecimal qty, 
                                      BigDecimal avgPrice, OffsetDateTime filledAt) {
        // 진행 중인 사이클 찾기
        Optional<TradeCycle> activeCycleOpt = tradeCycleRepository
                .findByUserAndStockAndEndDateIsNull(user, stock);
        
        if (activeCycleOpt.isEmpty()) {
            log.warn("진행 중인 TradeCycle이 없음: userId={}, symbol={}", user.getId(), stock.getSymbol());
            return;
        }
        
        TradeCycle cycle = activeCycleOpt.get();
        
        // Portfolio 확인하여 전량 매도인지 판단
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByUserAndStock(user, stock);
        boolean isFullSell = portfolioOpt.isEmpty() || portfolioOpt.get().getQuantity() == 0;
        
        if (isFullSell) {
            // 전량 매도 -> 사이클 종료
            LocalDateTime filledAtLocal = filledAt != null 
                    ? filledAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
            
            cycle.close(filledAtLocal, qty, avgPrice);
            tradeCycleRepository.save(cycle);
            
            log.info("TradeCycle 종료 (전량 매도): userId={}, symbol={}, cycleId={}, endDate={}, profitLossRate={}%", 
                    user.getId(), stock.getSymbol(), cycle.getId(), filledAtLocal, cycle.getProfitLossRate());
        } else {
            // 부분 매도 -> 평균 매도가 업데이트 (가중평균)
            cycle.updateOnPartialSell(qty, avgPrice);
            tradeCycleRepository.save(cycle);
            log.info("TradeCycle 업데이트 (부분 매도): userId={}, symbol={}, cycleId={}, qty={}, avgSellPrice={}", 
                    user.getId(), stock.getSymbol(), cycle.getId(), qty, cycle.getAverageSellPrice());
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
    
    // OrderRequest 업데이트(filledQuantity, filledAvgPrice)
    private void updateOrderFields(OrderRequest order, JsonNode orderNode) {
        log.debug("OrderRequest 기본 업데이트 완료: orderId={}", order.getId());
    }
}

