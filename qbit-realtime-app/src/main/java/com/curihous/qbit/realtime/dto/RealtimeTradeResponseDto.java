package com.curihous.qbit.realtime.dto;

import com.curihous.qbit.infra.binance.dto.websocket.BinanceTradeMessage;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실시간 체결 데이터 응답 DTO (WebSocket)
 * 
 * 사용 API:
 * - ws://{server}:{port}/ws/market/{symbol}
 * 
 * Binance WebSocket에서 받은 실시간 체결 데이터를 클라이언트에 전달
 */
@Schema(description = "실시간 체결 데이터")
public record RealtimeTradeResponseDto(
    @Schema(description = "체결가격", example = "264.23")
    Double price,
    
    @Schema(description = "종목 심볼", example = "BTCUSDT")
    String symbol,
    
    @Schema(description = "체결시간 (Unix timestamp)", example = "1582649824000")
    Long timestamp,
    
    @Schema(description = "체결수량", example = "100")
    Double volume
) {
    
    public static RealtimeTradeResponseDto from(BinanceTradeMessage tradeMessage) {
        return new RealtimeTradeResponseDto(
            Double.parseDouble(tradeMessage.getPrice()),
            tradeMessage.getSymbol(),
            tradeMessage.getTradeTime(),
            Double.parseDouble(tradeMessage.getQuantity())
        );
    }
}
