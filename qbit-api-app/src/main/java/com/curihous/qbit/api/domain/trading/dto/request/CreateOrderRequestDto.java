package com.curihous.qbit.api.domain.trading.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 주문 생성 요청 DTO
 * 
 * 사용 API:
 * - POST /trading/orders
 */
public record CreateOrderRequestDto(
    @NotBlank
    @Schema(description = "종목 심볼", example = "AAPL")
    String symbol,
    
    @NotBlank
    @Schema(description = "수량", example = "10")
    String quantity,
    
    @NotBlank
    @Schema(description = "매수/매도", example = "buy", allowableValues = {"buy", "sell"})
    String side,
    
    @NotBlank
    @Schema(description = "주문 유형", example = "market", allowableValues = {"market", "limit", "stop", "stop_limit"})
    String type,
    
    @Schema(description = "주문 유효기간 (생략 시 자동: us_equity=day, crypto=gtc)", example = "day", allowableValues = {"day", "gtc", "ioc", "fok"})
    String timeInForce,
    
    @Schema(description = "지정가 (limit/stop_limit 시 필수)", example = "150.50")
    String limitPrice,
    
    @Schema(description = "손절가 (stop/stop_limit 시 필수)", example = "145.00")
    String stopPrice
) {}

