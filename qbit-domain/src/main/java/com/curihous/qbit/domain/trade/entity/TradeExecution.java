package com.curihous.qbit.domain.trade.entity;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_execution_id")
    private Long id;

    @Column(name = "executed_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal executedQuantity;

    @Column(name = "executed_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal executedPrice;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_request_id", nullable = false)
    private OrderRequest orderRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public TradeExecution(BigDecimal executedQuantity, BigDecimal executedPrice, LocalDateTime executedAt,
                          OrderRequest orderRequest, User user) {
        this.executedQuantity = executedQuantity;
        this.executedPrice = executedPrice;
        this.executedAt = executedAt;
        this.orderRequest = orderRequest;
        this.user = user;
    }
}
