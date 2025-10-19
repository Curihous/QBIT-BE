package com.curihous.qbit.domain.portfolio.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long id;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "average_purchase_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal averagePurchasePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    public Portfolio(Integer quantity, BigDecimal averagePurchasePrice, User user, Stock stock) {
        this.quantity = quantity;
        this.averagePurchasePrice = averagePurchasePrice;
        this.user = user;
        this.stock = stock;
    }
    
    // 포트폴리오 업데이트 (매수/매도 시)
    public void updateQuantityAndPrice(Integer newQuantity, BigDecimal newAveragePurchasePrice) {
        this.quantity = newQuantity;
        this.averagePurchasePrice = newAveragePurchasePrice;
    }
    
    // 수량만 업데이트 (부분 매도 시)
    public void updateQuantity(Integer newQuantity) {
        this.quantity = newQuantity;
    }
}
