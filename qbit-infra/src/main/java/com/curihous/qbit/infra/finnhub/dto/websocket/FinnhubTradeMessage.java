package com.curihous.qbit.infra.finnhub.dto.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Finnhub WebSocket 실시간 체결 데이터 메시지
 * 
 * WebSocket 메시지 형식:
 * {
 *   "data": [
 *     { "p": 264.23, "s": "AAPL", "t": 1582649824000, "v": 100 }
 *   ],
 *   "type": "trade" (trade, ping, error, subscribe)
 * }
 * 
 * FinnhubWebsocketManager에서 처리
 */
public record FinnhubTradeMessage(
    @JsonProperty("data")
    @Schema(description = "체결 데이터 배열")
    List<TradeData> data,
    
    @JsonProperty("type")
    @Schema(description = "메시지 타입", example = "trade")
    String type
) {
    
    @Schema(description = "개별 체결 데이터")
    public record TradeData(
        @JsonProperty("p")
        @Schema(description = "체결가격 (Price)", example = "264.23")
        Double price,
        
        @JsonProperty("s")
        @Schema(description = "종목 심볼 (Symbol)", example = "AAPL")
        String symbol,
        
        @JsonProperty("t")
        @Schema(description = "체결시간 (Timestamp)", example = "1582649824000")
        Long timestamp,
        
        @JsonProperty("v")
        @Schema(description = "체결수량 (Volume)", example = "100")
        Long volume
    ) {}
}
