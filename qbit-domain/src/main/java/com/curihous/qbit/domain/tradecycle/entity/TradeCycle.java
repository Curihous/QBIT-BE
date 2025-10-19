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
    
    // 총 매수 수량 (가중평균 계산용)
    @Column(name = "total_bought_quantity", precision = 19, scale = 6)
    private BigDecimal totalBoughtQuantity;
    
    // 총 매수 금액 (가중평균 계산용)
    @Column(name = "total_bought_amount", precision = 19, scale = 6)
    private BigDecimal totalBoughtAmount;

    @Column(name = "average_sell_price")
    private BigDecimal averageSellPrice;
    
    // 총 매도 수량 (가중평균 계산용)
    @Column(name = "total_sold_quantity", precision = 19, scale = 6)
    private BigDecimal totalSoldQuantity;
    
    // 총 매도 금액 (가중평균 계산용)
    @Column(name = "total_sold_amount", precision = 19, scale = 6)
    private BigDecimal totalSoldAmount;

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
        this.totalBoughtQuantity = BigDecimal.ZERO;
        this.totalBoughtAmount = BigDecimal.ZERO;
        this.averageSellPrice = averageSellPrice;
        this.totalSoldQuantity = BigDecimal.ZERO;
        this.totalSoldAmount = BigDecimal.ZERO;
        this.peakInvestment = peakInvestment;
        this.maxDrawdown = maxDrawdown;
        this.user = user;
        this.stock = stock;
    }
    
    // 추가 매수 시 평균 매수가 재계산 (가중평균)
    public void updateOnAdditionalBuy(BigDecimal buyQuantity, BigDecimal buyAvgPrice) {
        BigDecimal buyAmount = buyQuantity.multiply(buyAvgPrice);
        
        // 총 매수 수량 및 금액 누적
        this.totalBoughtQuantity = this.totalBoughtQuantity.add(buyQuantity);
        this.totalBoughtAmount = this.totalBoughtAmount.add(buyAmount);
        
        // 가중 평균 매수가 계산
        if (this.totalBoughtQuantity.compareTo(BigDecimal.ZERO) > 0) {
            this.averageBuyPrice = this.totalBoughtAmount.divide(
                this.totalBoughtQuantity,
                8,
                java.math.RoundingMode.HALF_UP
            );
        }
        
        // totalInvestmentAmount도 함께 업데이트
        this.totalInvestmentAmount = this.totalBoughtAmount;
        
        // 최고 투자금액이 갱신되면 업데이트
        if (this.totalInvestmentAmount.compareTo(this.peakInvestment) > 0) {
            this.peakInvestment = this.totalInvestmentAmount;
        }
    }
    
    // 부분 매도 시 평균 매도가 계산 (가중평균)
    public void updateOnPartialSell(BigDecimal sellQuantity, BigDecimal sellAvgPrice) {
        BigDecimal sellAmount = sellQuantity.multiply(sellAvgPrice);
        
        // 총 매도 수량 및 금액 누적
        this.totalSoldQuantity = this.totalSoldQuantity.add(sellQuantity);
        this.totalSoldAmount = this.totalSoldAmount.add(sellAmount);

        if (this.totalSoldQuantity.compareTo(BigDecimal.ZERO) > 0) {
            this.averageSellPrice = this.totalSoldAmount.divide(
                this.totalSoldQuantity, 
                8, 
                java.math.RoundingMode.HALF_UP
            );
        }
    }
    
    // 사이클 종료 (전량 매도)
    public void close(LocalDateTime endDate, BigDecimal finalSellQuantity, BigDecimal finalSellAvgPrice) {
        this.endDate = endDate;
        
        // 최종 매도도 누적에 포함
        BigDecimal finalSellAmount = finalSellQuantity.multiply(finalSellAvgPrice);
        this.totalSoldQuantity = this.totalSoldQuantity.add(finalSellQuantity);
        this.totalSoldAmount = this.totalSoldAmount.add(finalSellAmount);
        
        // 최종 평균 매도가 계산
        if (this.totalSoldQuantity.compareTo(BigDecimal.ZERO) > 0) {
            this.averageSellPrice = this.totalSoldAmount.divide(
                this.totalSoldQuantity,
                8,
                java.math.RoundingMode.HALF_UP
            );
        }
        
        // 손익률 계산
        if (this.averageBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            this.profitLossRate = finalSellAvgPrice.subtract(this.averageBuyPrice)
                    .divide(this.averageBuyPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")); // 백분율
        }
    }
}
