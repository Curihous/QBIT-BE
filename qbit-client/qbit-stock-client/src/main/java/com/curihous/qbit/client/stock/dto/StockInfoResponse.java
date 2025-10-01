package com.curihous.qbit.client.stock.dto;

public record StockInfoResponse(
    String ticker,
    String stockName,
    String market,
    String sector,
    String logoUrl,
    String description
) {}
