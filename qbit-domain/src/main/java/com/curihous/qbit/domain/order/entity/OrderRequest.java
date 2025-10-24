package com.curihous.qbit.domain.order.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "order_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_request_id")
    private Long id;

    // Alpaca 주문 ID
    @NotNull
    @Column(name = "alpaca_order_id", nullable = false, unique = true)
    private String alpacaOrderId;

    // 종목 심볼
    @NotNull
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    // 주문 수량
    @NotNull
    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    // 체결된 수량
    @Column(name = "filled_quantity", precision = 20, scale = 8)
    private BigDecimal filledQuantity;

    // 주문 방향 (매수/매도)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private OrderSide side;

    // 주문 유형 (시장가/지정가)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private OrderType type;

    // 주문 유효기간
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, length = 10)
    private TimeInForce timeInForce;

    // 지정가 (limit order일 때만)
    @Column(name = "limit_price", precision = 20, scale = 8)
    private BigDecimal limitPrice;

    // 손절가 (stop order일 때만)
    @Column(name = "stop_price", precision = 20, scale = 8)
    private BigDecimal stopPrice;

    // 평균 체결가
    @Column(name = "filled_avg_price", precision = 20, scale = 8)
    private BigDecimal filledAvgPrice;

    // 주문 상태
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    // 클라이언트 주문 ID (주문 중복 방지용)
    @Column(name = "client_order_id", length = 100)
    private String clientOrderId;

    // Alpaca에서 주문 생성 시간
    @Column(name = "alpaca_created_at")
    private OffsetDateTime alpacaCreatedAt;

    // 주문 제출 시간
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    // 체결 완료 시간
    @Column(name = "filled_at")
    private OffsetDateTime filledAt;

    // 취소 시간
    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;
    
    // 거부 시간
    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;
    
    // 만료 시간
    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    // === 주문 수정 추적 필드 ===
    
    // 대체된 시간 (이 주문이 수정되어 대체된 시간)
    @Column(name = "replaced_at")
    private OffsetDateTime replacedAt;

    // 이 주문을 대체한 주문 ID 
    @Column(name = "replaced_by", length = 100)
    private String replacedBy;

    // 이 주문이 대체한 주문 ID 
    @Column(name = "replaces", length = 100)
    private String replaces;

    // === FK ===

    // 종목 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    // 주문한 사용자
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public OrderRequest(String alpacaOrderId, String symbol, BigDecimal quantity, BigDecimal filledQuantity,
                        OrderSide side, OrderType type, TimeInForce timeInForce, BigDecimal limitPrice,
                        BigDecimal stopPrice, BigDecimal filledAvgPrice, OrderStatus status, String clientOrderId, 
                        OffsetDateTime alpacaCreatedAt, OffsetDateTime submittedAt,
                        OffsetDateTime filledAt, OffsetDateTime canceledAt, OffsetDateTime rejectedAt, OffsetDateTime expiredAt,
                        OffsetDateTime replacedAt, String replacedBy, String replaces,
                        Stock stock, User user) {
        this.alpacaOrderId = alpacaOrderId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.side = side;
        this.type = type;
        this.timeInForce = timeInForce;
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.filledAvgPrice = filledAvgPrice;
        this.status = status;
        this.clientOrderId = clientOrderId;
        this.alpacaCreatedAt = alpacaCreatedAt;
        this.submittedAt = submittedAt;
        this.filledAt = filledAt;
        this.canceledAt = canceledAt;
        this.rejectedAt = rejectedAt;
        this.expiredAt = expiredAt;
        this.replacedAt = replacedAt;
        this.replacedBy = replacedBy;
        this.replaces = replaces;
        this.stock = stock;
        this.user = user;
    }

    // 주문이 대체되었을 때 호출
    public void markAsReplaced(String replacedBy, OffsetDateTime replacedAt) {
        this.replacedBy = replacedBy;
        this.replacedAt = replacedAt;
        this.status = OrderStatus.REPLACED;
    }
    
    // 주문이 취소되었을 때 호출
    public void markAsCanceled() {
        this.status = OrderStatus.CANCELED;
        this.canceledAt = OffsetDateTime.now();
    }
    
    // 주문 상태 업데이트 
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
    
    // 체결 정보 업데이트 (부분 체결, 완전 체결)
    public void updateFilledInfo(BigDecimal filledQty, BigDecimal filledAvgPrice, OffsetDateTime filledAt) {
        this.filledQuantity = filledQty;
        this.filledAvgPrice = filledAvgPrice;
        
        if (filledAt != null) {
            this.filledAt = filledAt;
        }
        
        // 체결 상태에 따라 상태 자동 업데이트
        if (filledQty.compareTo(this.quantity) >= 0) {
            this.status = OrderStatus.FILLED;
        } else if (filledQty.compareTo(BigDecimal.ZERO) > 0) {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }
    
    // 주문 거부 처리
    public void markAsRejected() {
        this.status = OrderStatus.REJECTED;
        this.rejectedAt = OffsetDateTime.now();
    }
    
    // Symbol 업데이트
    public void updateSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    // 시간 필드 업데이트 메서드들
    public void updateSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public void updateFilledAt(OffsetDateTime filledAt) {
        this.filledAt = filledAt;
    }
    
    public void updateCanceledAt(OffsetDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }
    
    public void updateRejectedAt(OffsetDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
    
    public void updateExpiredAt(OffsetDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }
    
    // 주문 만료 처리
    public void markAsExpired() {
        this.status = OrderStatus.EXPIRED;
        this.expiredAt = OffsetDateTime.now();
    }
}
