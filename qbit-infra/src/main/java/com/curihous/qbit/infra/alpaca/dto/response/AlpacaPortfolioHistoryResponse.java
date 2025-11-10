package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alpaca Portfolio History API 응답 DTO
 * 
 * Alpaca API: GET /v2/account/portfolio/history
 * QBIT API: GET /portfolios/overview
 */
public record AlpacaPortfolioHistoryResponse(
    @JsonProperty("timestamp")
    List<Long> timestamps,

    @JsonProperty("equity")
    List<BigDecimal> equities,

    @JsonProperty("profit_loss")
    List<BigDecimal> profitLosses,

    @JsonProperty("profit_loss_pct")
    List<BigDecimal> profitLossPercents,

    @JsonProperty("base_value")
    BigDecimal baseValue,

    @JsonProperty("timeframe")
    String timeframe
) {
}

