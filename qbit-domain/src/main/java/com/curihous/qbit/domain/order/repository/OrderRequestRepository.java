package com.curihous.qbit.domain.order.repository;

import com.curihous.qbit.domain.order.entity.OrderRequest;
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
    // 사용자의 주문 목록 조회 (최신순)
    List<OrderRequest> findByUserOrderByAlpacaCreatedAtDesc(User user);
    
    // 사용자의 주문 목록 조회 (페이징, 최신순)
    Page<OrderRequest> findByUser(User user, Pageable pageable);
    
    // 사용자의 특정 종목 주문 목록 조회 (페이징, 최신순)
    Page<OrderRequest> findByUserAndSymbol(User user, String symbol, Pageable pageable);
    
    // 사용자의 side별 주문 목록 조회 (페이징, 최신순)
    Page<OrderRequest> findByUserAndSide(User user, com.curihous.qbit.domain.order.entity.OrderSide side, Pageable pageable);
    
    // 사용자의 특정 종목 및 side별 주문 목록 조회 (페이징, 최신순)
    Page<OrderRequest> findByUserAndSymbolAndSide(User user, String symbol, com.curihous.qbit.domain.order.entity.OrderSide side, Pageable pageable);
    
    // 사용자의 특정 주문 조회
    Optional<OrderRequest> findByIdAndUser(Long id, User user);
    
    // Alpaca 주문 ID로 조회
    Optional<OrderRequest> findByAlpacaOrderId(String alpacaOrderId);
    
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
}
