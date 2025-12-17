package com.curihous.qbit.domain.order.repository;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderSide;
import com.curihous.qbit.domain.order.entity.OrderStatus;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
    // 사용자의 모든 주문 조회 (TradeCycle 후처리용)
    List<OrderRequest> findByUserOrderByAlpacaCreatedAtAsc(User user);
    
    // 사용자의 특정 주문 조회
    Optional<OrderRequest> findByIdAndUser(Long id, User user);
    
    // Alpaca 주문 ID로 조회
    Optional<OrderRequest> findByAlpacaOrderId(String alpacaOrderId);
    
    // Alpaca 주문 ID로 조회 (User, Stock 함께 조회)
    @Query("SELECT o FROM OrderRequest o JOIN FETCH o.user JOIN FETCH o.stock WHERE o.alpacaOrderId = :alpacaOrderId")
    Optional<OrderRequest> findByAlpacaOrderIdWithUserAndStock(@Param("alpacaOrderId") String alpacaOrderId);
    
    // 사용자의 Alpaca 주문 ID로 조회
    Optional<OrderRequest> findByAlpacaOrderIdAndUser(String alpacaOrderId, User user);
    
    // 특정 TradeCycle에 연결된 체결된 주문 조회 (FILLED, PARTIALLY_FILLED)
    @Query("SELECT DISTINCT req FROM OrderRequest req " +
           "WHERE req.tradeCycle.id = :tradeCycleId " +
           "AND req.status IN :statuses " +
           "ORDER BY req.filledAt ASC")
    List<OrderRequest> findByTradeCycleIdAndStatusIn(
        @Param("tradeCycleId") Long tradeCycleId,
        @Param("statuses") List<OrderStatus> statuses
    );
    
    // 사용자의 주문 목록 조회 (필터링: symbol, side, asset, hasJournal)
    @Query("SELECT req FROM OrderRequest req " +
           "LEFT JOIN TradeJournal tj ON tj.orderRequest.id = req.id " +
           "LEFT JOIN req.stock s " +
           "WHERE req.user = :user " +
           "AND (:symbol IS NULL OR req.symbol = :symbol) " +
           "AND (:sides IS NULL OR req.side IN :sides) " +
           "AND (:assetClass IS NULL OR s.assetClass = :assetClass) " +
           "AND (:hasJournal IS NULL OR (:hasJournal = true AND tj.id IS NULL) OR (:hasJournal = false AND tj.id IS NOT NULL)) " +
           "ORDER BY req.alpacaCreatedAt DESC")
    Page<OrderRequest> findByUserWithFilters(
        @Param("user") User user,
        @Param("symbol") String symbol,
        @Param("sides") List<OrderSide> sides,
        @Param("assetClass") String assetClass,
        @Param("hasJournal") Boolean hasJournal,
        Pageable pageable
    );
    
    // 사용자의 체결된 주문 조회 (월별 집계용)
    @Query("SELECT req FROM OrderRequest req " +
           "WHERE req.user = :user " +
           "AND req.status IN :statuses " +
           "AND req.filledAt IS NOT NULL " +
           "AND req.filledQuantity IS NOT NULL " +
           "AND req.filledQuantity > 0 " +
           "AND req.filledAvgPrice IS NOT NULL " +
           "ORDER BY req.filledAt ASC")
    List<OrderRequest> findFilledOrdersByUser(
        @Param("user") User user,
        @Param("statuses") List<OrderStatus> statuses
    );
}
