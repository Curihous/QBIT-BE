package com.curihous.qbit.domain.order.repository;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, Long> {
}
