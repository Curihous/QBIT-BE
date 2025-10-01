package com.curihous.qbit.client.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockPriceResponse(
    String ticker,
    String stockName,
    BigDecimal currentPrice,
    BigDecimal changeAmount,
    BigDecimal changeRate,
    BigDecimal volume,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    BigDecimal openPrice,
    LocalDateTime timestamp
) {}
