package com.curihous.qbit.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class AlpacaAccountResponse {

    @JsonProperty("alpaca_account_id")
    private String id;

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("buying_power")
    private BigDecimal buyingPower;

    @JsonProperty("cash")
    private BigDecimal cash;

    @JsonProperty("portfolio_value")
    private BigDecimal portfolioValue;

    @JsonProperty("pattern_day_trader")
    private boolean patternDayTrader;

    @JsonProperty("trading_blocked")
    private boolean tradingBlocked;

    @JsonProperty("transfers_blocked")
    private boolean transfersBlocked;

    @JsonProperty("account_blocked")
    private boolean accountBlocked;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("trade_suspended_by_user")
    private boolean tradeSuspendedByUser;

    @JsonProperty("multiplier")
    private String multiplier;

    @JsonProperty("shorting_enabled")
    private boolean shortingEnabled;

    @JsonProperty("equity")
    private BigDecimal equity;

    @JsonProperty("last_equity")
    private BigDecimal lastEquity;

    @JsonProperty("long_market_value")
    private BigDecimal longMarketValue;

    @JsonProperty("short_market_value")
    private BigDecimal shortMarketValue;

    @JsonProperty("initial_margin")
    private BigDecimal initialMargin;

    @JsonProperty("maintenance_margin")
    private BigDecimal maintenanceMargin;

    @JsonProperty("last_maintenance_margin")
    private BigDecimal lastMaintenanceMargin;

    @JsonProperty("sma")
    private BigDecimal sma;
}
