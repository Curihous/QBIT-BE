package com.curihous.qbit.api.domain.stock.dto.response;

import com.curihous.qbit.infra.massive.dto.response.MassiveLastQuoteResponse;
import com.curihous.qbit.infra.massive.dto.response.MassiveTickerResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실시간 시세 응답 DTO
 * 
 * - QBIT API: GET /stocks/crypto/quote/{binanceSymbol} (Binance API)
 * - QBIT API: GET /stocks/us-equity/quote/{ticker} (Massive.io API)
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
    
    public static QuoteResponseDto fromMassive(String ticker,
                                                MassiveTickerResponse previousClose,
                                                MassiveLastQuoteResponse lastQuote) {
        // 전일 데이터 추출
        MassiveTickerResponse.AggregateResult prevData = previousClose.getResults() != null 
                && !previousClose.getResults().isEmpty()
                ? previousClose.getResults().get(0)
                : null;
        
        // NBBO에서 현재가 추출 (bid와 ask의 중간값 사용)
        Double currentPrice = null;
        if (lastQuote.getResults() != null) {
            Double bid = lastQuote.getResults().getBid() != null 
                    ? lastQuote.getResults().getBid().getPrice() : null;
            Double ask = lastQuote.getResults().getAsk() != null 
                    ? lastQuote.getResults().getAsk().getPrice() : null;
            
            if (bid != null && ask != null) {
                currentPrice = (bid + ask) / 2.0;
            } else if (ask != null) {
                currentPrice = ask;
            } else if (bid != null) {
                currentPrice = bid;
            }
        }
        // NBBO에서 값을 찾지 못한 경우 전일 종가 사용
        if (currentPrice == null && prevData != null) {
            currentPrice = prevData.getClosePrice();
        }
        
        Double highPrice = prevData != null ? prevData.getHighPrice() : null;
        Double lowPrice = prevData != null ? prevData.getLowPrice() : null;
        Double openPrice = prevData != null ? prevData.getOpenPrice() : null;
        Double previousClosePrice = prevData != null ? prevData.getClosePrice() : null;
        
        // 변동가 및 변동률 계산
        Double priceChange = null;
        Double priceChangePercentage = null;
        
        if (currentPrice != null && previousClosePrice != null) {
            priceChange = currentPrice - previousClosePrice;
            if (previousClosePrice != 0) {
                priceChangePercentage = (priceChange / previousClosePrice) * 100;
            }
        }
        
        Long timestamp = prevData != null ? prevData.getTimestamp() : null;
        
        return new QuoteResponseDto(
            ticker,
            currentPrice,
            highPrice,
            lowPrice,
            openPrice,
            previousClosePrice,
            priceChange,
            priceChangePercentage,
            timestamp
        );
    }
}
