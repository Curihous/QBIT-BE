package com.curihous.qbit.infra.alpaca.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 주문 생성 요청 DTO
 * 
 * day 디폴트값 처리 때문에 class로 선언
 * 
 * 사용 API:
 * - Alpaca API: POST /v2/orders
 * - QBIT API: POST /trading/orders
 */
@Getter
@Builder
public class CreateOrderRequest {

    @Schema(description = "종목 심볼 (예: AAPL, TSLA)", example = "AAPL")
    @NotBlank(message = "종목 코드는 필수입니다")
    @JsonProperty("symbol")
    private String symbol;

    @Schema(description = "주문 수량 (fractional shares 지원)", example = "10.5")
    @NotNull(message = "주문 수량은 필수입니다")
    @Positive(message = "주문 수량은 0보다 커야 합니다")
    @JsonProperty("qty")
    private BigDecimal qty;

    @Schema(description = "주문 방향 (buy: 매수, sell: 매도)", example = "buy")
    @NotBlank(message = "주문 방향은 필수입니다")
    @Pattern(regexp = "^(buy|sell)$")
    @JsonProperty("side")
    private String side;

    @Schema(description = "주문 유형 (market: 시장가, limit: 지정가, stop: 손절, stop_limit: 손절 지정가)", example = "limit")
    @NotBlank(message = "주문 유형은 필수입니다")
    @Pattern(regexp = "^(market|limit|stop|stop_limit)$")
    @JsonProperty("type")
    private String type;

    @Schema(description = "주문 유효기간 (day: 당일, gtc: 체결될 때까지, ioc: 즉시 체결 또는 취소, fok: 전량 체결 또는 취소)", example = "gtc", defaultValue = "day")
    @Pattern(regexp = "^(day|gtc|ioc|fok)$")
    @JsonProperty("time_in_force")
    @Builder.Default
    private String timeInForce = "day";

    @Schema(description = "지정가 (limit/stop_limit 주문 시 필수)", example = "175.50")
    @JsonProperty("limit_price")
    private BigDecimal limitPrice;

    @Schema(description = "손절가 (stop/stop_limit 주문 시 필수, 트리거 가격)", example = "165.00")
    @JsonProperty("stop_price")
    private BigDecimal stopPrice;

    @Schema(description = "클라이언트 주문 ID (멱등성 보장 - 중복 주문 방지, 최대 48자)", example = "qbit-order-20240115-093000-abc123")
    @JsonProperty("client_order_id")
    private String clientOrderId;
}
