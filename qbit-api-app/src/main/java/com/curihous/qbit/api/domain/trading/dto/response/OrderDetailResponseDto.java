package com.curihous.qbit.api.domain.trading.dto.response;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 주문 상세 조회 응답 DTO
 * 
 * QBIT API: GET /trading/orders
 * QBIT API: GET /trading/orders/{orderId}
 */
public record OrderDetailResponseDto(
    
    @Schema(description = "주문 ID (내부 DB ID)", example = "12345")
    Long orderId,
    
    @Schema(description = "Alpaca 주문 ID (UUID)", example = "904837e3-3b76-47ec-b432-046db621571b")
    String alpacaOrderId,

    @Schema(description = "종목 코드", example = "TSLA")
    String symbol,

    @Schema(description = "주문 방향 (buy/sell)", example = "buy")
    String side,

    @Schema(description = "주문 수량 (fractional shares 지원)", example = "15.25")
    String quantity,
    
    @Schema(description = "체결된 수량", example = "15.25")
    String filledQuantity,
    
    @Schema(description = "평균 체결가", example = "242.68")
    String filledAvgPrice,

    @Schema(description = "주문 유형 (market/limit/stop/stop_limit)", example = "limit")
    String type,
    
    @Schema(description = "주문 유효기간 (day/gtc/ioc/fok)", example = "gtc")
    String timeInForce,

    @Schema(description = "지정가 (limit/stop_limit 주문인 경우)", example = "245.00")
    String limitPrice,

    @Schema(description = "손절가 (stop/stop_limit 주문인 경우)", example = "230.00")
    String stopPrice,

    @Schema(description = "주문 상태 (new/accepted/partially_filled/filled/canceled/rejected)", example = "filled")
    String status,

    @Schema(description = "주문 생성 시간 (RFC3339, UTC)", example = "2024-01-15T09:30:00.123456Z")
    OffsetDateTime createdAt,

    @Schema(description = "주문 제출 시간 (RFC3339, UTC)", example = "2024-01-15T09:30:01.234567Z")
    OffsetDateTime submittedAt,

    @Schema(description = "체결 완료 시간 (RFC3339, UTC)", example = "2024-01-15T09:30:15.345678Z")
    OffsetDateTime filledAt,

    @Schema(description = "취소 시간 (RFC3339, UTC)", example = "2024-01-15T10:00:00.456789Z")
    OffsetDateTime canceledAt,

    @Schema(description = "대체된 시간 (RFC3339, UTC)", example = "2024-01-15T09:45:00.567890Z")
    OffsetDateTime replacedAt,

    @Schema(description = "이 주문을 대체한 새 주문의 Alpaca ID (수정된 경우)", example = "61e69015-8549-4bfd-b9c3-01e75843f47d")
    String replacedBy,

    @Schema(description = "이 주문이 대체한 이전 주문의 Alpaca ID (수정 주문인 경우)", example = "904837e3-3b76-47ec-b432-046db621571b")
    String replaces
) {
    
    public static OrderDetailResponseDto from(OrderRequest orderRequest) {
        return new OrderDetailResponseDto(
                orderRequest.getId(),
                orderRequest.getAlpacaOrderId(),
                orderRequest.getSymbol(),
                orderRequest.getSide().name().toLowerCase(),
                orderRequest.getQuantity().toString(),
                orderRequest.getFilledQuantity() != null ? orderRequest.getFilledQuantity().toString() : "0",
                orderRequest.getFilledAvgPrice() != null ? orderRequest.getFilledAvgPrice().toString() : null,
                orderRequest.getType().name().toLowerCase(),
                orderRequest.getTimeInForce().name().toLowerCase(),
                orderRequest.getLimitPrice() != null ? orderRequest.getLimitPrice().toString() : null,
                orderRequest.getStopPrice() != null ? orderRequest.getStopPrice().toString() : null,
                orderRequest.getStatus().name().toLowerCase(),
                orderRequest.getAlpacaCreatedAt(),
                orderRequest.getSubmittedAt(),
                orderRequest.getFilledAt(),
                orderRequest.getCanceledAt(),
                orderRequest.getReplacedAt(),
                orderRequest.getReplacedBy(),
                orderRequest.getReplaces()
        );
    }
}

