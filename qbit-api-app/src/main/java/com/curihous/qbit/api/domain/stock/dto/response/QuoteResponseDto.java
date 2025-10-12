package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.curihous.qbit.infra.finnhub.dto.response.FinnhubQuoteResponse;


/**
 * 실시간 시세 응답 DTO
 * 
 * 사용 API:
 * - GET /market/quote/{symbol}
 */
public record QuoteResponseDto(
    @Schema(description = "종목 심볼", example = "AAPL")
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
    
    @Schema(description = "업데이트 시간 (Unix timestamp)", example = "1696887456000")
    Long timestamp
) {
    public static QuoteResponseDto from(String symbol, FinnhubQuoteResponse finnhubQuote) {
        return new QuoteResponseDto(
            symbol,
            finnhubQuote.currentPrice(),
            finnhubQuote.highPrice(),
            finnhubQuote.lowPrice(),
            finnhubQuote.openPrice(),
            finnhubQuote.previousClose(),
            finnhubQuote.priceChange(),
            finnhubQuote.priceChangePercentage(),
            System.currentTimeMillis() / 1000 // 현재 시간을 Unix timestamp로
        );
    }
}
