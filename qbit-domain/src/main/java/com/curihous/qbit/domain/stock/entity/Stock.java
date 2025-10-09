package com.curihous.qbit.domain.stock.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
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
    private Long id;

    // === Alpaca Assets API 응답 구조와 동일하게 ===
    
    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "exchange")
    private String exchange;

    @Column(name = "class")
    private String assetClass;

    @Column(name = "status")
    private String status;

    @Column(name = "tradable")
    private Boolean tradable;

    @Column(name = "fractionable")
    private Boolean fractionable;

    @Column(name = "min_order_size")
    private String minOrderSize;

    @Column(name = "min_trade_increment")
    private String minTradeIncrement;

    @Column(name = "price_increment")
    private String priceIncrement;

    // AlpacaAssetResponse로부터 Stock 생성
    @Builder
    public Stock(String symbol, String stockName, String exchange, String assetClass, String status,
                 Boolean tradable, Boolean fractionable,
                 String minOrderSize, String minTradeIncrement, String priceIncrement) {
        this.symbol = symbol;
        this.stockName = stockName;
        this.exchange = exchange;
        this.assetClass = assetClass;
        this.status = status;
        this.tradable = tradable;
        this.fractionable = fractionable;
        this.minOrderSize = minOrderSize;
        this.minTradeIncrement = minTradeIncrement;
        this.priceIncrement = priceIncrement;
    }

    // 종목 정보 업데이트 (배치 작업용)
    public void updateFromAlpaca(String stockName, String exchange, String assetClass, 
                                 String status, Boolean tradable, Boolean fractionable,
                                 String minOrderSize, String minTradeIncrement, String priceIncrement) {
        this.stockName = stockName;
        this.exchange = exchange;
        this.assetClass = assetClass;
        this.status = status;
        this.tradable = tradable;
        this.fractionable = fractionable;
        this.minOrderSize = minOrderSize;
        this.minTradeIncrement = minTradeIncrement;
        this.priceIncrement = priceIncrement;
    }
}
