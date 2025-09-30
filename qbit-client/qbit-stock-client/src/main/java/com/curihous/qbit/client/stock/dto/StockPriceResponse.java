package com.curihous.qbit.client.stock.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceResponse {
    
    private String ticker;
    private String stockName;
    private BigDecimal currentPrice;
    private BigDecimal changeAmount;
    private BigDecimal changeRate;
    private BigDecimal volume;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal openPrice;
    private LocalDateTime timestamp;
}
