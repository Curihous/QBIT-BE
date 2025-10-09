package com.curihous.qbit.infra.alpaca.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Alpaca 주문 정보 응답 DTO
 * 
 * 사용 API:
 * - Alpaca API: POST /v2/orders
 * - Alpaca API: GET /v2/orders
 * - Alpaca API: GET /v2/orders/{order_id}
 * - Alpaca API: PATCH /v2/orders/{order_id}
 * - Alpaca API: DELETE /v2/positions/{symbol}
 * - QBIT API: POST /trading/orders
 */
public record AlpacaOrderResponse(
    @Schema(description = "주문 ID")
    @JsonProperty("id")
    String id,

    @Schema(description = "클라이언트 주문 ID")
    @JsonProperty("client_order_id")
    String clientOrderId,

    @Schema(description = "주문 생성 시간")
    @JsonProperty("created_at")
    LocalDateTime createdAt,

    @Schema(description = "주문 제출 시간")
    @JsonProperty("submitted_at")
    LocalDateTime submittedAt,

    @Schema(description = "체결 완료 시간")
    @JsonProperty("filled_at")
    LocalDateTime filledAt,

    @Schema(description = "취소 시간")
    @JsonProperty("canceled_at")
    LocalDateTime canceledAt,

    @Schema(description = "대체된 시간")
    @JsonProperty("replaced_at")
    LocalDateTime replacedAt,

    @Schema(description = "이 주문을 대체한 주문 ID")
    @JsonProperty("replaced_by")
    String replacedBy,

    @Schema(description = "이 주문이 대체한 주문 ID")
    @JsonProperty("replaces")
    String replaces,

    @Schema(description = "종목 심볼")
    @JsonProperty("symbol")
    String symbol,

    @Schema(description = "주문 수량")
    @JsonProperty("qty")
    String qty,

    @Schema(description = "체결된 수량")
    @JsonProperty("filled_qty")
    String filledQty,

    @Schema(description = "평균 체결가")
    @JsonProperty("filled_avg_price")
    String filledAvgPrice,

    @Schema(description = "주문 유형 (market/limit/stop/stop_limit)")
    @JsonProperty("type")
    String type,

    @Schema(description = "주문 방향 (buy/sell)")
    @JsonProperty("side")
    String side,

    @Schema(description = "주문 유효기간 (day/gtc/ioc/fok)")
    @JsonProperty("time_in_force")
    String timeInForce,

    @Schema(description = "지정가")
    @JsonProperty("limit_price")
    String limitPrice,

    @Schema(description = "손절가")
    @JsonProperty("stop_price")
    String stopPrice,

    @Schema(description = "주문 상태")
    @JsonProperty("status")
    String status
) {
}
