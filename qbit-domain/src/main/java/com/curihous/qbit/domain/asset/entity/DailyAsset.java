package com.curihous.qbit.domain.asset.entity;

import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_asset_id")
    private Long dailyAssetId;

    @Column(name = "total_asset_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalAssetValue;

    @Column(name = "total_purchase_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalPurchaseAmount;

    @Column(name = "total_profit_loss", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalProfitLoss;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public DailyAsset(BigDecimal totalAssetValue, BigDecimal totalPurchaseAmount,
                      BigDecimal totalProfitLoss, LocalDate baseDate, LocalDateTime createdAt, User user) {
        this.totalAssetValue = totalAssetValue;
        this.totalPurchaseAmount = totalPurchaseAmount;
        this.totalProfitLoss = totalProfitLoss;
        this.baseDate = baseDate;
        this.createdAt = createdAt;
        this.user = user;
    }
}
