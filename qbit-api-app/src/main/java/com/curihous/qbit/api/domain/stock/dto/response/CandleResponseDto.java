package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import com.curihous.qbit.infra.finnhub.dto.response.FinnhubCandleResponse;

/**
 * 차트 데이터 응답 DTO
 * 
 * 사용 API:
 * - GET /market/candle/{symbol}?resolution=1&from=...&to=...
 */
public record CandleResponseDto(
    @Schema(description = "종목 심볼", example = "AAPL")
    String symbol,
    
    @Schema(description = "차트 해상도", example = "1")
    String resolution,
    
    @Schema(description = "상태", example = "ok")
    String status,
    
    @Schema(description = "캔들 데이터")
    List<CandleData> candles
) {
    
    public record CandleData(
        @Schema(description = "타임스탬프", example = "1696800000")
        Long timestamp,
        
        @Schema(description = "시가", example = "172.10")
        Double open,
        
        @Schema(description = "고가", example = "172.50")
        Double high,
        
        @Schema(description = "저가", example = "172.00")
        Double low,
        
        @Schema(description = "종가 ", example = "172.25")
        Double close,
        
        @Schema(description = "거래량", example = "1000000")
        Long volume
    ) {}
    
    public static CandleResponseDto from(String symbol, String resolution, FinnhubCandleResponse finnhubCandle) {
        List<CandleData> candles = List.of();
        
        if (finnhubCandle.status().equals("ok") && 
            finnhubCandle.timestamps() != null && 
            finnhubCandle.openPrices() != null) {
            
            candles = finnhubCandle.timestamps().stream()
                .mapToInt(finnhubCandle.timestamps()::indexOf)
                .mapToObj(i -> new CandleData(
                    finnhubCandle.timestamps().get(i),
                    finnhubCandle.openPrices().get(i),
                    finnhubCandle.highPrices().get(i),
                    finnhubCandle.lowPrices().get(i),
                    finnhubCandle.closePrices().get(i),
                    finnhubCandle.volumes().get(i)
                ))
                .toList();
        }
        
        return new CandleResponseDto(
            symbol,
            resolution,
            finnhubCandle.status(),
            candles
        );
    }
}
