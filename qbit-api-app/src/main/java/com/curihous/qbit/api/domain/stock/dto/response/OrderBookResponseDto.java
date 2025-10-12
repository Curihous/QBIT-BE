package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 암호화폐 호가창 응답 DTO
 * 
 * 사용 API:
 * - GET /market/orderbook/{symbol}
 */
@Schema(description = "암호화폐 호가창 정보")
public record OrderBookResponseDto(
    @Schema(description = "종목 심볼", example = "BINANCE:BTCUSDT")
    String symbol,
    
    @Schema(description = "매도 호가")
    List<OrderBookLevel> asks,
    
    @Schema(description = "매수 호가")
    List<OrderBookLevel> bids,
    
    @Schema(description = "업데이트 시간 (Unix timestamp)", example = "1696887456000")
    Long timestamp
) {
    
    @Schema(description = "호가 레벨")
    public record OrderBookLevel(
        @Schema(description = "가격", example = "27123.5")
        String price,
        
        @Schema(description = "수량", example = "0.002")
        String quantity
    ) {}
    
    public static OrderBookResponseDto from(String symbol, com.curihous.qbit.infra.finnhub.dto.response.FinnhubCryptoOrderBookResponse finnhubOrderBook) {
        List<OrderBookLevel> asks = finnhubOrderBook.asks().stream()
            .map(ask -> new OrderBookLevel(ask.get(0), ask.get(1)))
            .toList();
            
        List<OrderBookLevel> bids = finnhubOrderBook.bids().stream()
            .map(bid -> new OrderBookLevel(bid.get(0), bid.get(1)))
            .toList();
        
        return new OrderBookResponseDto(
            symbol,
            asks,
            bids,
            finnhubOrderBook.timestamp()
        );
    }
}
