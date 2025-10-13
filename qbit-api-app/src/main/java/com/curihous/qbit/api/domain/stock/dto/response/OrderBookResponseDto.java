package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 암호화폐 호가창 응답 DTO
 * 
 * QBIT API: GET /stocks/orderbook/{symbol}
 */
public record OrderBookResponseDto(
    @Schema(description = "종목 심볼", example = "BTCUSDT")
    String symbol,
    
    @Schema(description = "매도 호가")
    List<OrderBookLevel> asks,
    
    @Schema(description = "매수 호가")
    List<OrderBookLevel> bids,
    
    @Schema(description = "업데이트 시간 (Unix timestamp)", example = "1696887456000")
    Long lastUpdateId
) {
    
    @Schema(description = "호가 레벨")
    public record OrderBookLevel(
        @Schema(description = "가격", example = "27123.5")
        String price,
        
        @Schema(description = "수량", example = "0.002")
        String quantity
    ) {}
    
    public static OrderBookResponseDto of(String symbol, List<OrderBookLevel> asks, 
                                        List<OrderBookLevel> bids, Long lastUpdateId) {
        return new OrderBookResponseDto(symbol, asks, bids, lastUpdateId);
    }
}
