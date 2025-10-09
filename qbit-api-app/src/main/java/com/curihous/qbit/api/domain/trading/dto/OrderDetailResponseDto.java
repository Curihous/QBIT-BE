package com.curihous.qbit.api.domain.trading.dto;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 주문 상세 조회 응답 DTO
 * 
 * 사용 API:
 * - GET /trading/orders (목록)
 * - GET /trading/orders/{orderId} (상세)
 * 
 * 주문 히스토리/상세 화면에 표시할 정보
 */
@Schema(description = "주문 상세 조회 응답")
public record OrderDetailResponseDto(
    
    @Schema(description = "주문 ID")
    Long orderId,
    
    @Schema(description = "Alpaca 주문 ID")
    String alpacaOrderId,

    @Schema(description = "종목 코드", example = "AAPL")
    String symbol,

    @Schema(description = "주문 방향 (buy/sell)", example = "buy")
    String side,

    @Schema(description = "주문 수량", example = "10")
    String qty,
    
    @Schema(description = "체결된 수량", example = "5")
    String filledQty,
    
    @Schema(description = "평균 체결가", example = "150.25")
    String filledAvgPrice,

    @Schema(description = "주문 유형 (market/limit/stop/stop_limit)", example = "limit")
    String type,
    
    @Schema(description = "주문 유효기간 (day/gtc/ioc/fok)", example = "day")
    String timeInForce,

    @Schema(description = "지정가 (limit 주문인 경우)", example = "150.00")
    String limitPrice,

    @Schema(description = "손절가 (stop 주문인 경우)", example = "140.00")
    String stopPrice,

    @Schema(description = "주문 상태", example = "filled")
    String status,

    @Schema(description = "주문 생성 시간")
    LocalDateTime createdAt,

    @Schema(description = "주문 제출 시간")
    LocalDateTime submittedAt,

    @Schema(description = "체결 완료 시간")
    LocalDateTime filledAt,

    @Schema(description = "취소 시간")
    LocalDateTime canceledAt,

    @Schema(description = "대체된 시간 (수정된 경우)")
    LocalDateTime replacedAt,

    @Schema(description = "이 주문을 대체한 주문 ID (수정된 경우)")
    String replacedBy,

    @Schema(description = "이 주문이 대체한 주문 ID (수정 주문인 경우)")
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

