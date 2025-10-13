package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 암호화폐 실시간 시세 응답 DTO
 * 
 * QBIT API: GET /stocks/quote/{symbol}
 */
public record QuoteResponseDto(
    @Schema(description = "종목 심볼", example = "BTCUSDT")
    String symbol,
    
    @Schema(description = "현재가", example = "172.25")
    Double currentPrice,
    
    @Schema(description = "당일 최고가", example = "173.15")
    Double highPrice,
    
    @Schema(description = "당일 최저가", example = "171.80")
    Double lowPrice,
    
    @Schema(description = "시작가", example = "172.00")
    Double openPrice,
    
    @Schema(description = "전일 종가", example = "171.20")
    Double previousClose,
    
    @Schema(description = "전일 대비 변동가", example = "1.05")
    Double priceChange,
    
    @Schema(description = "전일 대비 변동률 (%)", example = "0.61")
    Double priceChangePercentage,
    
    @Schema(description = "업데이트 시간 (Unix timestamp, 밀리초)", example = "1696887456000")
    Long timestamp
) {
    public static QuoteResponseDto of(String symbol, Double currentPrice, Double highPrice, 
                                    Double lowPrice, Double openPrice, Double previousClose,
                                    Double priceChange, Double priceChangePercentage, Long timestamp) {
        return new QuoteResponseDto(symbol, currentPrice, highPrice, lowPrice, openPrice, 
                                  previousClose, priceChange, priceChangePercentage, timestamp);
    }
}
