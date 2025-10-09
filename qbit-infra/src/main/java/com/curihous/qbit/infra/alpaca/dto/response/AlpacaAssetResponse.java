package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alpaca 종목 정보 응답 DTO
 * 
 * 사용 API:
 * - Alpaca API: GET /v2/assets
 * - Alpaca API: GET /v2/assets/{symbol}
 * - QBIT API: GET /stocks
 * - QBIT API: GET /stocks/{symbol}
 */
public record AlpacaAssetResponse(
    @Schema(description = "종목 심볼 (예: AAPL, TSLA)")
    @JsonProperty("symbol")
    String symbol,

    @Schema(description = "종목명")
    @JsonProperty("name")
    String name,

    @Schema(description = "거래소")
    @JsonProperty("exchange")
    String exchange,

    @Schema(description = "자산 클래스")
    @JsonProperty("class")
    String assetClass,

    @Schema(description = "거래 상태")
    @JsonProperty("status")
    String status,

    @Schema(description = "거래 가능 여부")
    @JsonProperty("tradable")
    Boolean tradable,

    @Schema(description = "소수점 거래 가능 여부")
    @JsonProperty("fractionable")
    Boolean fractionable,

    @Schema(description = "최소 주문 수량")
    @JsonProperty("min_order_size")
    String minOrderSize,

    @Schema(description = "최소 거래 증분 (프론트엔드 수량 입력 폼 step 속성용)")
    @JsonProperty("min_trade_increment")
    String minTradeIncrement,

    @Schema(description = "가격 증분 (프론트엔드 가격 입력 폼 step 속성용)")
    @JsonProperty("price_increment")
    String priceIncrement
) {
}
