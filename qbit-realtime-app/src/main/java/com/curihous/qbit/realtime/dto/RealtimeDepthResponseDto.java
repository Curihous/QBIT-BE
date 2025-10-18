package com.curihous.qbit.realtime.dto;

import com.curihous.qbit.infra.binance.dto.websocket.BinanceDepthMessage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 실시간 호가창 데이터 응답 DTO (WebSocket)
 * 
 * 사용 API:
 * - ws://{server}:{port}/ws/depth/{symbol}
 * 
 */
public record RealtimeDepthResponseDto(
    @Schema(description = "종목 심볼", example = "BTCUSDT")
    String symbol,
    
    @Schema(description = "마지막 업데이트 ID", example = "1234567890")
    Long lastUpdateId,
    
    @Schema(description = "매수 호가 (가격 높은 순)")
    List<OrderBookLevel> bids,
    
    @Schema(description = "매도 호가 (가격 낮은 순)")
    List<OrderBookLevel> asks,
    
    @Schema(description = "타임스탬프 (Unix timestamp)", example = "1582649824000")
    Long timestamp
) {
    
    // 내부DTO: 호가창 레벨 (매수/매도 호가 한 줄)
    @Schema(description = "호가창 레벨")
    public record OrderBookLevel(
        @Schema(description = "가격", example = "43250.50")
        Double price,
        
        @Schema(description = "수량", example = "1.5")
        Double quantity
    ) {
        
        public static OrderBookLevel from(java.util.List<String> priceQuantity) {
            if (priceQuantity == null || priceQuantity.size() < 2) {
                return null;
            }
            return new OrderBookLevel(
                Double.parseDouble(priceQuantity.get(0)), // 가격
                Double.parseDouble(priceQuantity.get(1))  // 수량
            );
        }
    }
    
    public static RealtimeDepthResponseDto from(String symbol, BinanceDepthMessage depthMessage) {
        List<OrderBookLevel> bids = depthMessage.getBids().stream()
            .map(OrderBookLevel::from)
            .filter(level -> level != null)
            .collect(Collectors.toList());
        
        List<OrderBookLevel> asks = depthMessage.getAsks().stream()
            .map(OrderBookLevel::from)
            .filter(level -> level != null)
            .collect(Collectors.toList());
        
        return new RealtimeDepthResponseDto(
            symbol,
            depthMessage.getLastUpdateId(),
            bids,
            asks,
            System.currentTimeMillis()
        );
    }
}

