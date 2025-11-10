package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alpaca Portfolio History API 응답 DTO
 * 
 * Alpaca API: GET /v2/account/portfolio/history
 * QBIT API: GET /portfolios/overview
 */
public record AlpacaPortfolioHistoryResponse(
    @Nullable
    @JsonProperty("timestamp")
    List<Long> timestamps,

    @Nullable
    @JsonProperty("equity")
    List<BigDecimal> equities,

    @Nullable
    @JsonProperty("profit_loss")
    List<BigDecimal> profitLosses,

    @Nullable
    @JsonProperty("profit_loss_pct")
    List<BigDecimal> profitLossPercents,

    @Nullable
    @JsonProperty("base_value")
    BigDecimal baseValue,

    @Nullable
    @JsonProperty("timeframe")
    String timeframe
) {
}

