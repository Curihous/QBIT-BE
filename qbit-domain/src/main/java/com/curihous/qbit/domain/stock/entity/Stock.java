package com.curihous.qbit.domain.stock.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "market", nullable = false)
    private String market;

    @Column(name = "ticker", nullable = false, unique = true)
    private String ticker;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "logo_url")
    private String logoUrl;

    public Stock(String market, String ticker, String stockName, String logoUrl) {
        this.market = market;
        this.ticker = ticker;
        this.stockName = stockName;
        this.logoUrl = logoUrl;
    }
}
