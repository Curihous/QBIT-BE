package com.curihous.qbit.api.domain.stock.dto.response;

import com.curihous.qbit.infra.massive.dto.response.MassiveSnapshotResponse;
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
    
    // Massive.io Snapshot API에서 시세 정보 추출
    public static QuoteResponseDto fromMassiveSnapshot(String ticker, MassiveSnapshotResponse snapshot) {
        if (snapshot == null || snapshot.getTicker() == null) {
            throw new IllegalArgumentException("Invalid snapshot response");
        }
        
        MassiveSnapshotResponse.TickerData tickerData = snapshot.getTicker();
        MassiveSnapshotResponse.DayData day = tickerData.getDay();
        MassiveSnapshotResponse.PrevDayData prevDay = tickerData.getPrevDay();
        
        // 당일 데이터
        Double currentPrice = day != null ? day.getClose() : null;  // 종가 = 현재가
        Double highPrice = day != null ? day.getHigh() : null;
        Double lowPrice = day != null ? day.getLow() : null;
        Double openPrice = day != null ? day.getOpen() : null;
        Long timestamp = day != null ? day.getTimestamp() : null;
        
        // 전일 종가
        Double previousClosePrice = prevDay != null ? prevDay.getClose() : null;
        
        // 변동가 및 변동률 계산
        Double priceChange = null;
        Double priceChangePercentage = null;
        
        if (currentPrice != null && previousClosePrice != null) {
            priceChange = currentPrice - previousClosePrice;
            if (previousClosePrice != 0) {
                priceChangePercentage = (priceChange / previousClosePrice) * 100;
            }
        }
        
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
