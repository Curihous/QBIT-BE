package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alpaca 포지션 정보 응답 DTO
 * 
 * 사용 API:
 * - Alpaca API: GET /v2/positions
 * - Alpaca API: GET /v2/positions/{symbol}
 */
public record AlpacaPositionResponse(
    @Schema(description = "자산 ID")
    @JsonProperty("asset_id")
    String assetId,

    @Schema(description = "종목 심볼")
    @JsonProperty("symbol")
    String symbol,

    @Schema(description = "거래소")
    @JsonProperty("exchange")
    String exchange,

    @Schema(description = "자산 클래스")
    @JsonProperty("asset_class")
    String assetClass,

    @Schema(description = "평균 매수가")
    @JsonProperty("avg_entry_price")
    String avgEntryPrice,

    @Schema(description = "보유 수량")
    @JsonProperty("qty")
    String quantity,

    @Schema(description = "포지션 방향 (long/short)")
    @JsonProperty("side")
    String side,

    @Schema(description = "시장 가치")
    @JsonProperty("market_value")
    String marketValue,

    @Schema(description = "원가 기준")
    @JsonProperty("cost_basis")
    String costBasis,

    @Schema(description = "미실현 손익")
    @JsonProperty("unrealized_pl")
    String unrealizedPl,

    @Schema(description = "미실현 손익률")
    @JsonProperty("unrealized_plpc")
    String unrealizedPlpc,

    @Schema(description = "당일 미실현 손익")
    @JsonProperty("unrealized_intraday_pl")
    String unrealizedIntradayPl,

    @Schema(description = "당일 미실현 손익률")
    @JsonProperty("unrealized_intraday_plpc")
    String unrealizedIntradayPlpc,

    @Schema(description = "현재 가격")
    @JsonProperty("current_price")
    String currentPrice,

    @Schema(description = "전일 종가")
    @JsonProperty("lastday_price")
    String lastdayPrice,

    @Schema(description = "당일 변동")
    @JsonProperty("change_today")
    String changeToday,

    @Schema(description = "거래 가능 수량")
    @JsonProperty("qty_available")
    String quantityAvailable
) {
}
