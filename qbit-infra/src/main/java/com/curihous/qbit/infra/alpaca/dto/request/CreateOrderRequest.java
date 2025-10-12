package com.curihous.qbit.infra.alpaca.dto.request;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 주문 생성 요청 DTO
 * 
 * Factory 메서드를 통해 주문 유형별 필수 필드를 강제:
 * - market(): 시장가 주문
 * - limit(): 지정가 주문 (limitPrice 필수)
 * - stop(): 손절 주문 (stopPrice 필수)
 * - stopLimit(): 손절지정가 주문 (stopPrice, limitPrice 필수)
 * 
 * 사용 API:
 * - Alpaca API: POST /v2/orders
 * - QBIT API: POST /trading/orders
 */
@Getter
public class CreateOrderRequest {

    @Schema(description = "종목 심볼 (예: AAPL, TSLA)", example = "AAPL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @JsonProperty("symbol")
    private String symbol;

    @Schema(description = "자산 클래스 (us_equity, crypto)", example = "us_equity")
    @JsonProperty("asset_class")
    private String assetClass;

    @Schema(description = "주문 수량 (fractional shares 지원)", example = "10.5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Positive(message = "주문 수량은 0보다 커야 합니다")
    @JsonProperty("qty")
    private BigDecimal qty;

    @Schema(description = "주문 방향 (buy: 매수, sell: 매도)", example = "buy", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Pattern(regexp = "^(buy|sell)$")
    @JsonProperty("side")
    private String side;

    @Schema(description = "주문 유형 (market: 시장가, limit: 지정가, stop: 손절, stop_limit: 손절 지정가)", example = "limit", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Pattern(regexp = "^(market|limit|stop|stop_limit)$")
    @JsonProperty("type")
    private String type;

    @Schema(description = "주문 유효기간 (day: 당일, gtc: 체결될 때까지, ioc: 즉시 체결 또는 취소, fok: 전량 체결 또는 취소)", example = "gtc", defaultValue = "day")
    @Pattern(regexp = "^(day|gtc|ioc|fok)$")
    @JsonProperty("time_in_force")
    private String timeInForce;

    @Schema(description = "지정가 (limit/stop_limit 주문 시 필수)", example = "175.50")
    @JsonProperty("limit_price")
    private BigDecimal limitPrice;

    @Schema(description = "손절가 (stop/stop_limit 주문 시 필수, 트리거 가격)", example = "165.00")
    @JsonProperty("stop_price")
    private BigDecimal stopPrice;

    @Schema(description = "클라이언트 주문 ID (멱등성 보장 - 중복 주문 방지. 클라이언트가 생성)", example = "qbit-order-20240115-093000-abc123")
    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonCreator
    private CreateOrderRequest(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("asset_class") String assetClass,
            @JsonProperty("qty") BigDecimal qty,
            @JsonProperty("side") String side,
            @JsonProperty("type") String type,
            @JsonProperty("time_in_force") String timeInForce,
            @JsonProperty("limit_price") BigDecimal limitPrice,
            @JsonProperty("stop_price") BigDecimal stopPrice,
            @JsonProperty("client_order_id") String clientOrderId
    ) {
        this.symbol = symbol;
        this.assetClass = assetClass;
        this.qty = qty;
        this.side = side;
        this.type = type;
        this.timeInForce = timeInForce != null ? timeInForce : getDefaultTimeInForce(assetClass);
        this.limitPrice = limitPrice;
        this.stopPrice = stopPrice;
        this.clientOrderId = clientOrderId;
        validate();
    }

    // Factory 메서드: 시장가 주문
    public static CreateOrderRequest market(String symbol, String assetClass, BigDecimal qty, String side) {
        return new CreateOrderRequest(symbol, assetClass, qty, side, "market", null, null, null, null);
    }

    // Factory 메서드: 지정가 주문
    public static CreateOrderRequest limit(String symbol, String assetClass, BigDecimal qty, String side, BigDecimal limitPrice) {
        if (limitPrice == null) {
            throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, "지정가 주문은 limitPrice가 필수입니다");
        }
        return new CreateOrderRequest(symbol, assetClass, qty, side, "limit", null, limitPrice, null, null);
    }

    // Factory 메서드: 손절 주문
    public static CreateOrderRequest stop(String symbol, String assetClass, BigDecimal qty, String side, BigDecimal stopPrice) {
        if (stopPrice == null) {
            throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, "손절 주문은 stopPrice가 필수입니다");
        }
        return new CreateOrderRequest(symbol, assetClass, qty, side, "stop", null, null, stopPrice, null);
    }

    // Factory 메서드: 손절지정가 주문
    public static CreateOrderRequest stopLimit(String symbol, String assetClass, BigDecimal qty, String side, BigDecimal stopPrice, BigDecimal limitPrice) {
        if (stopPrice == null) {
            throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, "손절지정가 주문은 stopPrice가 필수입니다");
        }
        if (limitPrice == null) {
            throw new QbitException(ErrorCode.INVALID_ORDER_PRICE, "손절지정가 주문은 limitPrice가 필수입니다");
        }
        return new CreateOrderRequest(symbol, assetClass, qty, side, "stop_limit", null, limitPrice, stopPrice, null);
    }

    // setter
    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    // JSON 역직렬화 시(클라이언트 → QBIT) 주문 유형별 필수 필드 검증
    private void validate() {
        // 기본 필드는 Bean Validation(@NotBlank)이 처리
        if (type == null) {
            return; 
        }

        // 주문 유형별 필수 필드 검증
        switch (type) {
            case "limit":
                if (limitPrice == null) {
                    throw new IllegalArgumentException("limit 주문은 limitPrice가 필수입니다");
                }
                break;
            case "stop":
                if (stopPrice == null) {
                    throw new IllegalArgumentException("stop 주문은 stopPrice가 필수입니다");
                }
                break;
            case "stop_limit":
                if (stopPrice == null) {
                    throw new IllegalArgumentException("stop_limit 주문은 stopPrice가 필수입니다");
                }
                if (limitPrice == null) {
                    throw new IllegalArgumentException("stop_limit 주문은 limitPrice가 필수입니다");
                }
                break;
            case "market":
                // 시장가는 추가 필드 불필요
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 주문 유형입니다: " + type);
        }
    }
    
    /**
     * 자산 클래스에 따른 기본 time_in_force 값 반환
     * - us_equity: day (당일)
     * - crypto: gtc (체결될 때까지)
     */
    private static String getDefaultTimeInForce(String assetClass) {
        if ("crypto".equalsIgnoreCase(assetClass)) {
            return "gtc";
        }
        return "day";
    }
}
