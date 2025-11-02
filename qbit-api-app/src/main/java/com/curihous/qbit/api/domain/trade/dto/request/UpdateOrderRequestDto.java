package com.curihous.qbit.api.domain.trade.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 수정 요청 DTO
 * 
 * 사용 API:
 * - PATCH /trading/orders/{orderId}
 */
public record UpdateOrderRequestDto(
    @Schema(description = "수정할 수량", example = "15")
    String quantity,
    
    @Schema(description = "수정할 지정가", example = "152.00")
    String limitPrice,
    
    @Schema(description = "수정할 손절가", example = "147.00")
    String stopPrice,
    
    @Schema(description = "수정할 주문 유효기간", example = "gtc")
    String timeInForce
) {}

