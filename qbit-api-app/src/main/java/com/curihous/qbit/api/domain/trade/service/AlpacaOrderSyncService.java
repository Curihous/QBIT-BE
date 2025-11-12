package com.curihous.qbit.api.domain.trade.service;

import com.curihous.qbit.common.event.TradeUpdateEvent;
import com.curihous.qbit.common.event.LoginOrderSyncEvent;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
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
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.repository.TradeCycleRepository;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import com.curihous.qbit.infra.alpaca.client.AlpacaTradingClient;
import com.curihous.qbit.infra.alpaca.dto.request.AlpacaOrderQueryParams;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final PortfolioRepository portfolioRepository;
    private final TradeCycleRepository tradeCycleRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final AlpacaTradingClient alpacaTradingClient;
    private final AlpacaOrderTransactionalService alpacaOrderTransactionalService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String REDIS_TOKEN_KEY_PREFIX = "alpaca:token:";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    // ============== 로그인 시 주문 동기화 ==============
    
    // 로그인 시 주문 동기화 이벤트 처리 (qbit-api-app 내부 처리)
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
            
            // 토큰을 Redis에 저장 (qbit-realtime-app에서 조회 가능하도록)
            if (event.getAccessToken() != null && !event.getAccessToken().isEmpty()) {
                saveTokenToRedis(user.getId(), event.getAccessToken());
            } else {
                syncTokenToRedis(user.getId());
            }
            
            // 주문 내역 동기화 (Alpaca API에서 최신 주문 내역을 가져와서 DB에 저장)
            syncOrdersOnLogin(user);
            
        } catch (Exception e) {
            log.error("로그인 시 주문 동기화 이벤트 처리 실패: userId={}, error={}", 
                    event.getUserId(), e.getMessage(), e);
        }
    }
    
    // 토큰을 직접 받아서 Redis에 저장
    // AlpacaOAuthService에서 이벤트에 accessToken을 포함시켜 발행한 경우 이벤트에서 직접 accessToken을 받아서 DB 조회 없이 Redis에 저장
    private void saveTokenToRedis(Long userId, String accessToken) {
        try {
            if (accessToken == null || accessToken.isEmpty()) {
                log.debug("Alpaca 토큰이 없어 Redis 저장 건너뜀: userId={}", userId);
                return;
            }
            
            // Redis에 토큰 저장 (qbit-realtime-app에서 조회 가능하도록)
            // Key: "alpaca:token:{userId}"
            // TTL: 7일
            String redisKey = REDIS_TOKEN_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(redisKey, accessToken, TOKEN_TTL);
            
            log.debug("Alpaca 토큰 Redis 저장 완료: userId={}, ttl={}일", userId, TOKEN_TTL.toDays());
            
        } catch (Exception e) {
            log.error("Alpaca 토큰 Redis 저장 실패: userId={}, error={}", 
                    userId, e.getMessage(), e);
        }
    }
    
    // DB에서 Alpaca 토큰 조회 후 Redis에 저장
    // 이벤트에 accessToken이 없는 경우 (KakaoLoginService, GoogleLoginService에서 발행한 이벤트) DB에서 토큰을 조회하여 Redis에 저장
    private void syncTokenToRedis(Long userId) {
        try {
            Optional<AlpacaOAuthConnection> connectionOpt = 
                    alpacaOAuthConnectionService.findByUserId(userId);
            
            if (connectionOpt.isEmpty()) {
                log.debug("Alpaca 연결이 없어 Redis 동기화 건너뜀: userId={}", userId);
                return;
            }
            
            AlpacaOAuthConnection connection = connectionOpt.get();
            if (!connection.getAlpacaConnectionStatus().equals(AlpacaConnectionStatus.ACTIVE)) {
                log.debug("비활성화된 Alpaca 연결: userId={}", userId);
                return;
            }
            
            String accessToken = connection.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.debug("Alpaca 토큰이 없어 Redis 동기화 건너뜀: userId={}", userId);
                return;
            }
            
            // DB에서 조회한 토큰을 Redis에 저장
            saveTokenToRedis(userId, accessToken);
            
        } catch (Exception e) {
            log.error("Alpaca 토큰 Redis 동기화 실패: userId={}, error={}", 
                    userId, e.getMessage(), e);
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
            log.info("Alpaca 주문 조회 시작: userId={}, status=all, limit=500", user.getId());
            
            List<AlpacaOrderResponse> alpacaOrders;
            try {
                AlpacaOrderQueryParams queryParams = AlpacaOrderQueryParams.builder()
                        .status("all")
                        .limit(500)
                        .direction("desc")
                        .nested(true)
                        .build();
                alpacaOrders = alpacaTradingClient.getOrders(authorization, queryParams);
                log.info("Alpaca에서 주문 조회 완료: userId={}, 주문 수={}", user.getId(), alpacaOrders != null ? alpacaOrders.size() : 0);
                
                if (alpacaOrders == null) {
                    log.warn("Alpaca 주문 조회 결과가 null입니다: userId={}", user.getId());
                    alpacaOrders = List.of();
                }
            } catch (Exception e) {
                log.error("Alpaca 주문 조회 실패: userId={}, error={}", user.getId(), e.getMessage(), e);
                return;
            }
            
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
        updateOrderStatus(event);
        updatePortfolio(event);
        updateTradeCycle(event);
    }
    
    // 부분 체결 이벤트 처리
    private void handlePartialFillEvent(TradeUpdateEvent event) {
        updateOrderStatus(event);   
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
                Optional<OrderRequest> orderOpt = orderRequestRepository
                        .findByAlpacaOrderId(event.getAlpacaOrderId());
                
                if (orderOpt.isPresent()) {
                    OrderRequest order = orderOpt.get();
                    handleSellTradeCycle(user, stock, filledQty, filledAvgPrice, filledAt, order);
                } else {
                    log.warn("OrderRequest를 찾을 수 없어 TradeCycle 업데이트 건너뜀: alpacaOrderId={}", 
                            event.getAlpacaOrderId());
                }
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
                                      BigDecimal avgPrice, OffsetDateTime filledAt,
                                      OrderRequest order) {
        if (order == null) {
            log.warn("OrderRequest가 null이어서 TradeCycle 업데이트 건너뜀: userId={}, symbol={}", 
                    user.getId(), stock.getSymbol());
            return;
        }
        
        Optional<TradeCycle> activeCycleOpt = tradeCycleRepository
                .findByUserAndStockAndEndDateIsNull(user, stock);
        
        if (activeCycleOpt.isEmpty()) {
            log.warn("진행 중인 TradeCycle이 없음: userId={}, symbol={}", user.getId(), stock.getSymbol());
            return;
        }
        
        TradeCycle cycle = activeCycleOpt.get();
        
        BigDecimal filledQuantity = order.getFilledQuantity();
        BigDecimal orderQuantity = order.getQuantity();
        
        boolean isFullSell = false;
        if (filledQuantity != null && orderQuantity != null) {
            isFullSell = filledQuantity.compareTo(orderQuantity) >= 0;
            log.debug("OrderRequest 기준 전량 매도 확인: alpacaOrderId={}, quantity={}, filledQuantity={}, isFullSell={}",
                    order.getAlpacaOrderId(), orderQuantity, filledQuantity, isFullSell);
        } else {
            log.warn("OrderRequest의 quantity 또는 filledQuantity가 null: alpacaOrderId={}, quantity={}, filledQuantity={}",
                    order.getAlpacaOrderId(), orderQuantity, filledQuantity);
            return;
        }
        
        if (isFullSell) {
            LocalDateTime filledAtLocal = filledAt != null 
                    ? filledAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();
            
            cycle.close(filledAtLocal, qty, avgPrice);
            tradeCycleRepository.save(cycle);
            
            log.info("TradeCycle 종료 (전량 매도): userId={}, symbol={}, cycleId={}, profitLossRate={}%, alpacaOrderId={}", 
                    user.getId(), stock.getSymbol(), cycle.getId(), cycle.getProfitLossRate(),
                    order.getAlpacaOrderId());
        } else {
            cycle.updateOnPartialSell(qty, avgPrice);
            tradeCycleRepository.save(cycle);
            log.info("TradeCycle 업데이트 (부분 매도): userId={}, symbol={}, cycleId={}, alpacaOrderId={}", 
                    user.getId(), stock.getSymbol(), cycle.getId(), order.getAlpacaOrderId());
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
    
    // tradeCycle 임시 메서드
    @Transactional
    public int backfillTradeCycles(Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.error("사용자를 찾을 수 없음: userId={}", userId);
                return 0;
            }
            
            User user = userOpt.get();
            log.info("TradeCycle 후처리 시작 (OrderRequest 기준): userId={}", userId);
            
            // 사용자의 모든 OrderRequest를 시간순으로 조회
            List<OrderRequest> allOrders = orderRequestRepository.findByUserOrderByAlpacaCreatedAtDesc(user);
            
            if (allOrders.isEmpty()) {
                log.info("주문 내역이 없음: userId={}", userId);
                return 0;
            }
            
            // 시간순 정렬 (오름차순)
            allOrders.sort((o1, o2) -> {
                OffsetDateTime time1 = o1.getAlpacaCreatedAt() != null ? o1.getAlpacaCreatedAt() : o1.getCreatedAt().atOffset(java.time.ZoneOffset.UTC);
                OffsetDateTime time2 = o2.getAlpacaCreatedAt() != null ? o2.getAlpacaCreatedAt() : o2.getCreatedAt().atOffset(java.time.ZoneOffset.UTC);
                return time1.compareTo(time2);
            });
            
            // 사용자의 모든 완료된 TradeCycle 조회 (기간 필터링용)
            List<TradeCycle> completedCycles = tradeCycleRepository.findByUserAndEndDateIsNotNull(user);
            
            // TradeCycle의 startDate ~ endDate 범위에 있는 주문은 제외
            final List<TradeCycle> finalCompletedCycles = completedCycles;
            List<OrderRequest> unprocessedOrders = allOrders.stream()
                    .filter(order -> {
                        if (order.getFilledAt() == null) {
                            return true; // 체결되지 않은 주문도 포함
                        }
                        LocalDateTime filledAt = order.getFilledAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                        // 완료된 TradeCycle 중 이 주문의 체결 시각이 포함되는 기간이 있는지 확인
                        return finalCompletedCycles.stream().noneMatch(cycle -> {
                            LocalDateTime startDate = cycle.getStartDate();
                            LocalDateTime endDate = cycle.getEndDate();
                            return !filledAt.isBefore(startDate)
                                    && !filledAt.isAfter(endDate);
                        });
                    })
                    .collect(Collectors.toList());
            
            if (unprocessedOrders.isEmpty()) {
                log.debug("처리되지 않은 주문 없음: userId={}", userId);
                return 0;
            }
            
            log.debug("처리되지 않은 주문 필터링: userId={}, 전체={}, 미처리={}", 
                    userId, allOrders.size(), unprocessedOrders.size());
            
            // 종목별로 그룹화
            Map<String, List<OrderRequest>> ordersBySymbol = unprocessedOrders.stream()
                    .collect(Collectors.groupingBy(OrderRequest::getSymbol));
            
            int createdCount = 0;
            
            // 각 종목별로 TradeCycle 생성 시도
            for (Map.Entry<String, List<OrderRequest>> entry : ordersBySymbol.entrySet()) {
                String symbol = entry.getKey();
                List<OrderRequest> symbolOrders = entry.getValue();
                
                Optional<Stock> stockOpt = stockRepository.findBySymbol(symbol);
                if (stockOpt.isEmpty()) {
                    log.warn("종목을 찾을 수 없음: symbol={}, userId={}", symbol, userId);
                    continue;
                }
                
                Stock stock = stockOpt.get();
                
                // 진행 중인 TradeCycle이 있는지 확인
                Optional<TradeCycle> ongoingCycleOpt = tradeCycleRepository
                        .findByUserAndStockAndEndDateIsNull(user, stock);
                
                if (ongoingCycleOpt.isPresent()) {
                    log.debug("진행 중인 TradeCycle이 이미 존재: userId={}, symbol={}", userId, symbol);
                    continue;
                }
                
                // OrderRequest 기준으로 TradeCycle 생성 시도
                Optional<TradeCycle> createdCycle = createTradeCycleFromOrders(user, stock, symbolOrders);
                
                if (createdCycle.isPresent()) {
                    createdCount++;
                    log.info("TradeCycle 생성 완료: userId={}, symbol={}, cycleId={}", 
                            userId, symbol, createdCycle.get().getId());
                }
            }
            
            log.info("TradeCycle 후처리 완료: userId={}, 생성된 TradeCycle 수={}", userId, createdCount);
            return createdCount;
            
        } catch (Exception e) {
            log.error("TradeCycle 후처리 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw new QbitException(ErrorCode.TRADE_CYCLE_BACKFILL_FAILED, "TradeCycle 후처리 실패: userId=" + userId, e);
        }
    }
    
    // OrderRequest 기준으로 TradeCycle 생성 (전량 매도 시점 역추적)
    private Optional<TradeCycle> createTradeCycleFromOrders(User user, Stock stock, 
                                                             List<OrderRequest> orders) {
        if (orders.isEmpty()) {
            return Optional.empty();
        }
        
        BigDecimal currentQuantity = BigDecimal.ZERO;
        BigDecimal currentAverageBuyPrice = BigDecimal.ZERO;
        BigDecimal totalBoughtQuantity = BigDecimal.ZERO;
        BigDecimal totalInvestmentAmount = BigDecimal.ZERO;
        BigDecimal totalSoldQuantity = BigDecimal.ZERO;
        BigDecimal totalSoldAmount = BigDecimal.ZERO;
        
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        int endIndex = orders.size();
        
        // peakInvestment와 maxDrawdown 계산용 변수
        BigDecimal peakInvestment = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal currentInvestment = BigDecimal.ZERO;
        BigDecimal highestInvestment = BigDecimal.ZERO;
        
        for (int i = 0; i < orders.size(); i++) {
            OrderRequest order = orders.get(i);
            
            // 체결되지 않은 주문은 스킵
            if (order.getFilledQuantity() == null || order.getFilledQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            LocalDateTime orderTime = order.getFilledAt() != null 
                    ? order.getFilledAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                    : (order.getAlpacaCreatedAt() != null 
                            ? order.getAlpacaCreatedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                            : LocalDateTime.now());
            
            if (startDate == null) {
                startDate = orderTime;
            }
            
            if (order.getSide() == com.curihous.qbit.domain.order.entity.OrderSide.BUY) {
                BigDecimal qty = order.getFilledQuantity();
                BigDecimal price = order.getFilledAvgPrice() != null ? order.getFilledAvgPrice() : BigDecimal.ZERO;
                
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("매수 주문의 평균 체결가가 없음: orderId={}, alpacaOrderId={}", 
                            order.getId(), order.getAlpacaOrderId());
                    continue;
                }
                
                BigDecimal buyAmount = qty.multiply(price);
                if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
                    currentAverageBuyPrice = price;
                    currentQuantity = qty;
                } else {
                    BigDecimal currentTotalCost = currentQuantity.multiply(currentAverageBuyPrice);
                    BigDecimal newTotalCost = currentTotalCost.add(buyAmount);
                    BigDecimal newQuantity = currentQuantity.add(qty);
                    currentAverageBuyPrice = newTotalCost.divide(newQuantity, 8, RoundingMode.HALF_UP);
                    currentQuantity = newQuantity;
                }
                
                totalBoughtQuantity = totalBoughtQuantity.add(qty);
                totalInvestmentAmount = totalInvestmentAmount.add(buyAmount);
                
                currentInvestment = currentQuantity.multiply(currentAverageBuyPrice);
                if (currentInvestment.compareTo(highestInvestment) > 0) {
                    highestInvestment = currentInvestment;
                }
                peakInvestment = highestInvestment;
                
            } else if (order.getSide() == com.curihous.qbit.domain.order.entity.OrderSide.SELL) {
                BigDecimal qty = order.getFilledQuantity();
                BigDecimal price = order.getFilledAvgPrice() != null ? order.getFilledAvgPrice() : BigDecimal.ZERO;
                
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("매도 주문의 평균 체결가가 없음: orderId={}, alpacaOrderId={}", 
                            order.getId(), order.getAlpacaOrderId());
                    continue;
                }
                
                boolean isFullSell = order.getFilledQuantity().compareTo(order.getQuantity()) >= 0;
                
                currentQuantity = currentQuantity.subtract(qty);
                
                totalSoldQuantity = totalSoldQuantity.add(qty);
                totalSoldAmount = totalSoldAmount.add(qty.multiply(price));
                
                if (currentQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    currentInvestment = currentQuantity.multiply(currentAverageBuyPrice);
                } else {
                    currentInvestment = BigDecimal.ZERO;
                }
                
                if (peakInvestment.compareTo(BigDecimal.ZERO) > 0 && currentInvestment.compareTo(peakInvestment) < 0) {
                    BigDecimal drawdown = peakInvestment.subtract(currentInvestment)
                            .divide(peakInvestment, 8, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")); // 백분율
                    if (drawdown.compareTo(maxDrawdown) > 0) {
                        maxDrawdown = drawdown;
                    }
                }
                
                // 전량 매도된 시점이면 TradeCycle 종료
                if (isFullSell && currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    endDate = orderTime;
                    endIndex = i + 1;
                    log.debug("전량 매도 시점 발견: orderId={}, alpacaOrderId={}, filledAt={}, quantity={}, filledQuantity={}",
                            order.getId(), order.getAlpacaOrderId(), orderTime, order.getQuantity(), order.getFilledQuantity());
                    break;
                }
            }
        }
        
        // 전량 매도가 완료되지 않으면 TradeCycle 생성 안 함
        if (endDate == null || totalBoughtQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("전량 매도 미완료 또는 매수 내역 없음: userId={}, symbol={}", user.getId(), stock.getSymbol());
            return Optional.empty();
        }
        
        BigDecimal averageBuyPrice = totalInvestmentAmount.divide(
                totalBoughtQuantity, 8, RoundingMode.HALF_UP);
        
        BigDecimal averageSellPrice = totalSoldAmount.divide(
                totalSoldQuantity, 8, RoundingMode.HALF_UP);
        
        BigDecimal profitLossRate = averageSellPrice.subtract(averageBuyPrice)
                .divide(averageBuyPrice, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        if (peakInvestment.compareTo(BigDecimal.ZERO) == 0) {
            peakInvestment = totalInvestmentAmount;
        }
        
        TradeCycle tradeCycle = new TradeCycle(
                startDate,
                endDate,
                profitLossRate,
                totalInvestmentAmount,
                averageBuyPrice,
                averageSellPrice,
                peakInvestment,
                maxDrawdown,
                user,
                stock
        );
        
        // 각 주문별로 TradeCycle 업데이트
        for (int i = 0; i < endIndex; i++) {
            OrderRequest order = orders.get(i);
            if (order.getFilledQuantity() == null || order.getFilledQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            BigDecimal qty = order.getFilledQuantity();
            BigDecimal price = order.getFilledAvgPrice() != null ? order.getFilledAvgPrice() : BigDecimal.ZERO;
            
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            
            if (order.getSide() == com.curihous.qbit.domain.order.entity.OrderSide.BUY) {
                tradeCycle.updateOnAdditionalBuy(qty, price);
            } else if (order.getSide() == com.curihous.qbit.domain.order.entity.OrderSide.SELL) {
                tradeCycle.updateOnPartialSell(qty, price);
            }
        }
        
        tradeCycleRepository.save(tradeCycle);
        return Optional.of(tradeCycle);
    }
    
}

