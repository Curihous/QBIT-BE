package com.curihous.qbit.domain.order.repository;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
    // 사용자의 주문 목록 조회 (최신순)
    List<OrderRequest> findByUserOrderByAlpacaCreatedAtDesc(User user);
    
    // 사용자의 특정 주문 조회
    Optional<OrderRequest> findByIdAndUser(Long id, User user);
    
    // Alpaca 주문 ID로 조회
    Optional<OrderRequest> findByAlpacaOrderId(String alpacaOrderId);
}
