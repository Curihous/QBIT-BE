package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Alpaca 계정 정보 응답 DTO
 * 
 * 사용 API:
 * - Alpaca API: GET /v2/account
 */
public record AlpacaAccountResponse(
    @Schema(description = "계정 ID")
    @JsonProperty("id")
    String id,

    @Schema(description = "계정 번호")
    @JsonProperty("account_number")
    String accountNumber,

    @Schema(description = "계정 상태")
    @JsonProperty("status")
    String status,

    @Schema(description = "통화")
    @JsonProperty("currency")
    String currency,

    @Schema(description = "매수 가능 금액")
    @JsonProperty("buying_power")
    BigDecimal buyingPower,

    @Schema(description = "현금")
    @JsonProperty("cash")
    BigDecimal cash,

    @Schema(description = "포트폴리오 가치")
    @JsonProperty("portfolio_value")
    BigDecimal portfolioValue,

    @Schema(description = "패턴 데이 트레이더 여부")
    @JsonProperty("pattern_day_trader")
    boolean patternDayTrader,

    @Schema(description = "거래 차단 여부")
    @JsonProperty("trading_blocked")
    boolean tradingBlocked,

    @Schema(description = "송금 차단 여부")
    @JsonProperty("transfers_blocked")
    boolean transfersBlocked,

    @Schema(description = "계정 차단 여부")
    @JsonProperty("account_blocked")
    boolean accountBlocked,

    @Schema(description = "계정 생성 시간")
    @JsonProperty("created_at")
    LocalDateTime createdAt,

    @Schema(description = "사용자에 의한 거래 중단 여부")
    @JsonProperty("trade_suspended_by_user")
    boolean tradeSuspendedByUser,

    @Schema(description = "레버리지 배수")
    @JsonProperty("multiplier")
    String multiplier,

    @Schema(description = "공매도 가능 여부")
    @JsonProperty("shorting_enabled")
    boolean shortingEnabled,

    @Schema(description = "자산 가치")
    @JsonProperty("equity")
    BigDecimal equity,

    @Schema(description = "전일 자산 가치")
    @JsonProperty("last_equity")
    BigDecimal lastEquity,

    @Schema(description = "롱 포지션 시장 가치")
    @JsonProperty("long_market_value")
    BigDecimal longMarketValue,

    @Schema(description = "숏 포지션 시장 가치")
    @JsonProperty("short_market_value")
    BigDecimal shortMarketValue,

    @Schema(description = "초기 증거금")
    @JsonProperty("initial_margin")
    BigDecimal initialMargin,

    @Schema(description = "유지 증거금")
    @JsonProperty("maintenance_margin")
    BigDecimal maintenanceMargin,

    @Schema(description = "전일 유지 증거금")
    @JsonProperty("last_maintenance_margin")
    BigDecimal lastMaintenanceMargin,

    @Schema(description = "SMA (Special Memorandum Account)")
    @JsonProperty("sma")
    BigDecimal sma
) {
}
