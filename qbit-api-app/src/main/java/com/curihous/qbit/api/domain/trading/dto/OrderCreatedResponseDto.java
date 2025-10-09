package com.curihous.qbit.api.domain.trading.dto;

import com.curihous.qbit.domain.order.entity.OrderRequest;
import com.curihous.qbit.domain.order.entity.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 주문 생성 성공 응답 DTO
 * 
 * 사용 API:
 * - POST /trading/orders
 * 
 * 주문 생성 성공 모달에 표시할 정보
 */
@Schema(description = "주문 생성 성공 응답")
public record OrderCreatedResponseDto(
    
    @Schema(description = "종목 코드")
    String symbol,

    @Schema(description = "주문 방향 (buy/sell)")
    String side,

    @Schema(description = "주문 수량")
    String qty,

    @Schema(description = "주문 유형")
    String orderType,

    @Schema(description = "주문 상태")
    String status,

    @Schema(description = "주문 시간")
    LocalDateTime orderedAt,

    @Schema(description = "주문 ID (조회용)")
    Long orderId,
    
    @Schema(description = "지정가 주문 정보 (limit 유형인 경우)")
    LimitOrderInfo limitOrder,

    @Schema(description = "시장가 주문 정보 (market 유형인 경우)")
    MarketOrderInfo marketOrder
) {
    
    // 지정가 주문 정보
    public record LimitOrderInfo(
        @Schema(description = "지정가격", example = "150.00")
        String limitPrice,

        @Schema(description = "총 주문 금액", example = "1500.00")
        String totalAmount
    ) {}
    
    // 시장가 주문 정보
    public record MarketOrderInfo(
        @Schema(description = "현재 시장가 (참고용)")
        String currentPrice
    ) {}
    
    public static OrderCreatedResponseDto from(OrderRequest orderRequest) {
        LimitOrderInfo limitOrder = null;
        MarketOrderInfo marketOrder = null;
        
        if (orderRequest.getType() == OrderType.LIMIT) {
            // 지정가 주문
            String totalAmount = orderRequest.getLimitPrice()
                    .multiply(orderRequest.getQuantity())
                    .toString();
            limitOrder = new LimitOrderInfo(
                orderRequest.getLimitPrice().toString(),
                totalAmount
            );
        } else {
            // 시장가 주문
            marketOrder = new MarketOrderInfo(
                null // TODO: 실시간 가격 조회
            );
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
                marketOrder
        );
    }
}
