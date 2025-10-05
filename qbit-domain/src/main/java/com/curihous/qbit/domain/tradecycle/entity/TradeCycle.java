package com.curihous.qbit.domain.tradecycle.entity;

import com.curihous.qbit.domain.stock.entity.Stock;
import com.curihous.qbit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_cycles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trade_cycle_id")
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "profit_loss_rate", nullable = false)
    private BigDecimal profitLossRate;

    @Column(name = "total_investment_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal totalInvestmentAmount;

    @Column(name = "average_buy_price", nullable = false)
    private BigDecimal averageBuyPrice;

    @Column(name = "average_sell_price")
    private BigDecimal averageSellPrice;

    @Column(name = "peak_investment")
    private BigDecimal peakInvestment;

    @Column(name = "max_drawdown", precision = 19, scale = 6)
    private BigDecimal maxDrawdown;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    public TradeCycle(LocalDateTime startDate, LocalDateTime endDate, BigDecimal profitLossRate,
                      BigDecimal totalInvestmentAmount, BigDecimal averageBuyPrice, BigDecimal averageSellPrice,
                      BigDecimal peakInvestment, BigDecimal maxDrawdown, User user, Stock stock) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.profitLossRate = profitLossRate;
        this.totalInvestmentAmount = totalInvestmentAmount;
        this.averageBuyPrice = averageBuyPrice;
        this.averageSellPrice = averageSellPrice;
        this.peakInvestment = peakInvestment;
        this.maxDrawdown = maxDrawdown;
        this.user = user;
        this.stock = stock;
    }
}
