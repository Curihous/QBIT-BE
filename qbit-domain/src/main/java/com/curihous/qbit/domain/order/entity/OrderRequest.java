package com.curihous.qbit.domain.order.entity;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_request_id")
    private Long orderRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "order_price", precision = 20, scale = 8)
    private BigDecimal orderPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public OrderRequest(OrderType orderType, Integer quantity, BigDecimal orderPrice,
                        OrderStatus orderStatus, LocalDateTime requestedAt, Stock stock, User user) {
        this.orderType = orderType;
        this.quantity = quantity;
        this.orderPrice = orderPrice;
        this.orderStatus = orderStatus;
        this.requestedAt = requestedAt;
        this.stock = stock;
        this.user = user;
    }

    public enum OrderType {
        BUY, SELL
    }

    public enum OrderStatus {
        REQUESTED, PARTIALLY_FILLED, FILLED, CANCELED
    }
}
