package com.curihous.qbit.api.domain.trading.dto.response;

import com.curihous.qbit.domain.order.port.TradingPort;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 수정 응답 DTO
 * 
 * QBIT API: PATCH /trading/orders/{orderId}
 */
public record OrderUpdateResponseDto(
    @Schema(description = "Alpaca 주문 ID")
    String alpacaOrderId,
    
    @Schema(description = "종목 심볼")
    String symbol,
    
    @Schema(description = "주문 수량")
    String quantity,
    
    @Schema(description = "체결 수량")
    String filledQuantity,
    
    @Schema(description = "매수/매도")
    String side,
    
    @Schema(description = "주문 유형")
    String type,
    
    @Schema(description = "주문 유효기간")
    String timeInForce,
    
    @Schema(description = "지정가")
    String limitPrice,
    
    @Schema(description = "손절가")
    String stopPrice,
    
    @Schema(description = "평균 체결가")
    String filledAvgPrice,
    
    @Schema(description = "주문 상태")
    String status,
    
    @Schema(description = "클라이언트 주문 ID")
    String clientOrderId,
    
    @Schema(description = "생성 시간")
    String createdAt,
    
    @Schema(description = "제출 시간")
    String submittedAt,
    
    @Schema(description = "체결 시간")
    String filledAt,
    
    @Schema(description = "취소 시간")
    String canceledAt,
    
    @Schema(description = "대체 시간")
    String replacedAt,
    
    @Schema(description = "대체된 주문 ID")
    String replacedBy,
    
    @Schema(description = "대체하는 주문 ID")
    String replaces
) {
    public static OrderUpdateResponseDto from(TradingPort.OrderUpdateResult result) {
        return new OrderUpdateResponseDto(
            result.alpacaOrderId(),
            result.symbol(),
            result.quantity(),
            result.filledQuantity(),
            result.side(),
            result.type(),
            result.timeInForce(),
            result.limitPrice(),
            result.stopPrice(),
            result.filledAvgPrice(),
            result.status(),
            result.clientOrderId(),
            result.createdAt(),
            result.submittedAt(),
            result.filledAt(),
            result.canceledAt(),
            result.replacedAt(),
            result.replacedBy(),
            result.replaces()
        );
    }
}

