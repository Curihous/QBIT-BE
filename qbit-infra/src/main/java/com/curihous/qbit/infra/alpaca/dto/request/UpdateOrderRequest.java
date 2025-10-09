package com.curihous.qbit.infra.alpaca.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 주문(미체결/부분체결 상태) 수정 요청 DTO
 *
 * 사용 API:
 * - Alpaca API: PATCH /v2/orders/{order_id}
 * - QBIT API: PATCH /trading/orders/{orderId}
 */
public record UpdateOrderRequest(
    @Schema(description = "수정할 주문 수량 (기존과 다른 값)", example = "15.25")
    @JsonProperty("qty")
    BigDecimal qty,

    @Schema(description = "수정할 주문 유효기간 (day: 당일, gtc: 체결될 때까지, ioc: 즉시 체결 또는 취소, fok: 전량 체결 또는 취소)", example = "gtc")
    @JsonProperty("time_in_force")
    String timeInForce,

    @Schema(description = "수정할 지정가 (limit/stop_limit 주문인 경우)", example = "180.00")
    @JsonProperty("limit_price")
    BigDecimal limitPrice,

    @Schema(description = "수정할 손절가 (stop/stop_limit 주문인 경우, 트리거 가격)", example = "170.00")
    @JsonProperty("stop_price")
    BigDecimal stopPrice,

    @Schema(description = "클라이언트 주문 ID (멱등성 보장 - 중복 방지)", example = "qbit-order-update-20240115-094500-xyz789")
    @JsonProperty("client_order_id")
    String clientOrderId
) {
}
