package com.curihous.qbit.domain.stock.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_indices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_index_id")
    private Long id;

    @Column(name = "symbol", nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "country", length = 50)
    private String country;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "current_price", precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "previous_close", precision = 19, scale = 4)
    private BigDecimal previousClose;

    @Column(name = "change_amount", precision = 19, scale = 4)
    private BigDecimal changeAmount;

    @Column(name = "change_percentage", precision = 8, scale = 4)
    private BigDecimal changePercentage;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "market_cap")
    private Long marketCap;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "is_active")
    private Boolean isActive;

    @Builder
    public MarketIndex(String symbol, String name, String description, String country, 
                      String currency, BigDecimal currentPrice, BigDecimal previousClose,
                      BigDecimal changeAmount, BigDecimal changePercentage, Long volume,
                      Long marketCap, LocalDateTime lastUpdated, Boolean isActive) {
        this.symbol = symbol;
        this.name = name;
        this.description = description;
        this.country = country;
        this.currency = currency;
        this.currentPrice = currentPrice;
        this.previousClose = previousClose;
        this.changeAmount = changeAmount;
        this.changePercentage = changePercentage;
        this.volume = volume;
        this.marketCap = marketCap;
        this.lastUpdated = lastUpdated;
        this.isActive = isActive != null ? isActive : true;
    }

    public void updatePrice(BigDecimal currentPrice, BigDecimal previousClose, 
                           BigDecimal changeAmount, BigDecimal changePercentage) {
        this.currentPrice = currentPrice;
        this.previousClose = previousClose;
        this.changeAmount = changeAmount;
        this.changePercentage = changePercentage;
        this.lastUpdated = LocalDateTime.now();
    }

    public void updateVolume(Long volume) {
        this.volume = volume;
        this.lastUpdated = LocalDateTime.now();
    }

    // 상승/하락 여부 확인
    public boolean isPositive() {
        return changeAmount != null && changeAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isNegative() {
        return changeAmount != null && changeAmount.compareTo(BigDecimal.ZERO) < 0;
    }
}
