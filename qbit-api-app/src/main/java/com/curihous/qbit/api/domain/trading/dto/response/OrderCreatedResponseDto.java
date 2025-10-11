package com.curihous.qbit.api.domain.trading.dto.response;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 주문 생성 성공 응답 DTO
 * 
 * 사용 API:
 * - POST /trading/orders
 */
@Schema(description = "주문 생성 성공 응답")
public record OrderCreatedResponseDto(
    
    @Schema(description = "종목 코드", example = "AAPL")
    String symbol,

    @Schema(description = "주문 방향 (buy/sell)", example = "buy")
    String side,

    @Schema(description = "주문 수량", example = "10.5")
    String qty,

    @Schema(description = "주문 유형 (market/limit/stop/stop_limit)", example = "limit")
    String orderType,

    @Schema(description = "주문 상태 (new/accepted/partially_filled/filled/canceled)", example = "accepted")
    String status,

    @Schema(description = "주문 시간 (RFC3339, UTC)", example = "2024-01-15T09:30:00.123456Z")
    OffsetDateTime orderedAt,

    @Schema(description = "주문 ID (조회용)", example = "12345")
    Long orderId,
    
    @Schema(description = "지정가 주문 정보 (limit 유형인 경우)")
    LimitOrderInfo limitOrder,

    @Schema(description = "시장가 주문 정보 (market 유형인 경우)")
    MarketOrderInfo marketOrder,

    @Schema(description = "손절/손절지정 주문 정보 (stop/stop_limit 유형인 경우)")
    StopOrderInfo stopOrder
) {
    
    // 지정가 주문 정보
    public record LimitOrderInfo(
        @Schema(description = "지정가격", example = "175.50")
        String limitPrice,

        @Schema(description = "총 주문 금액 (지정가 × 수량)", example = "1842.75")
        String totalAmount
    ) {}
    
    // 시장가 주문 정보
    public record MarketOrderInfo(
        @Schema(description = "현재 시장가 (참고용, 실시간 가격)", example = "176.25")
        String currentPrice
    ) {}

    // 손절/손절지정 주문 정보
    public record StopOrderInfo(
        @Schema(description = "손절가 (트리거 가격)", example = "165.00")
        String stopPrice,

        @Schema(description = "지정가 (stop_limit인 경우, null이면 stop market)", example = "164.50")
        String limitPrice
    ) {}
    
    public static OrderCreatedResponseDto from(OrderRequest orderRequest) {
        LimitOrderInfo limitOrder = null;
        MarketOrderInfo marketOrder = null;
        StopOrderInfo stopOrder = null;
        
        switch (orderRequest.getType()) {
            case LIMIT -> {
                // 지정가 주문
                String totalAmount = orderRequest.getLimitPrice()
                        .multiply(orderRequest.getQuantity())
                        .toString();
                limitOrder = new LimitOrderInfo(
                    orderRequest.getLimitPrice().toString(),
                    totalAmount
                );
            }
            case MARKET -> {
                // 시장가 주문
                marketOrder = new MarketOrderInfo(
                    null // TODO: 실시간 가격 조회
                );
            }
            case STOP, STOP_LIMIT -> {
                // 손절/손절지정 주문
                stopOrder = new StopOrderInfo(
                    orderRequest.getStopPrice() != null ? orderRequest.getStopPrice().toString() : null,
                    orderRequest.getLimitPrice() != null ? orderRequest.getLimitPrice().toString() : null
                );
            }
        }
        
        return new OrderCreatedResponseDto(
                orderRequest.getSymbol(),
                orderRequest.getSide().name().toLowerCase(),
                orderRequest.getQuantity().toString(),
                orderRequest.getType().name().toLowerCase(),
                orderRequest.getStatus().name().toLowerCase(),
                orderRequest.getAlpacaCreatedAt(),
                orderRequest.getId(),
                limitOrder,
                marketOrder,
                stopOrder
        );
    }
}

