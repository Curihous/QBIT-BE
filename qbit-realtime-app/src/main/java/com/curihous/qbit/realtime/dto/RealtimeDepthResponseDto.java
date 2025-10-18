package com.curihous.qbit.realtime.dto;

import com.curihous.qbit.infra.binance.dto.websocket.BinanceDepthMessage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
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
        BigDecimal price,
        
        @Schema(description = "수량", example = "1.5")
        BigDecimal quantity
    ) {
        
        public static OrderBookLevel from(java.util.List<String> priceQuantity) {
            if (priceQuantity == null || priceQuantity.size() < 2) {
                return null;
            }
            try {
                return new OrderBookLevel(
                    new BigDecimal(priceQuantity.get(0)), // 가격
                    new BigDecimal(priceQuantity.get(1))  // 수량
                );
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
    
    public static RealtimeDepthResponseDto from(String symbol, BinanceDepthMessage depthMessage) {
        // null-safety: null인 경우 빈 리스트로 처리
        List<OrderBookLevel> bids = (depthMessage.getBids() == null 
            ? Collections.<java.util.List<String>>emptyList() 
            : depthMessage.getBids())
            .stream()
            .map(OrderBookLevel::from)
            .filter(level -> level != null)
            .collect(Collectors.toList());
        
        List<OrderBookLevel> asks = (depthMessage.getAsks() == null 
            ? Collections.<java.util.List<String>>emptyList() 
            : depthMessage.getAsks())
            .stream()
            .map(OrderBookLevel::from)
            .filter(level -> level != null)
            .collect(Collectors.toList());
        
        // 정렬 보장: 매수 내림차순(가격 높은 순), 매도 오름차순(가격 낮은 순)
        bids.sort(Comparator.comparing(OrderBookLevel::price).reversed());
        asks.sort(Comparator.comparing(OrderBookLevel::price));
        
        return new RealtimeDepthResponseDto(
            symbol,
            depthMessage.getLastUpdateId(),
            bids,
            asks,
            System.currentTimeMillis()
        );
    }
}

