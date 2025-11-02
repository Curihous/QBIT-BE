package com.curihous.qbit.api.domain.stock.dto.response;

import com.curihous.qbit.infra.massive.dto.response.MassiveAggregateResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 차트 데이터 응답 DTO
 * 
 * QBIT API: GET /stocks/crypto/candle/{binanceSymbol} (Binance API)
 * QBIT API: GET /stocks/us-equity/candle/{ticker} (Massive.io API)
 */
public record CandleResponseDto(
    @Schema(description = "종목 심볼", example = "BTCUSDT")
    String symbol,
    
    @Schema(description = "차트 해상도", example = "1d")
    String interval,
    
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
        Double volume
    ) {}
    
    public static CandleResponseDto of(String symbol, String interval, List<CandleData> candles) {
        return new CandleResponseDto(symbol, interval, candles);
    }
    
    // Binance Kline(캔들스틱 차트 데이터)를 CandleResponseDto로 변환
    // Kline = Binance API 용어로 캔들스틱 차트 데이터 (OHLCV)
    public static CandleResponseDto fromBinance(String symbol, String interval, List<List<String>> binanceKlines) {
        List<CandleData> candles = binanceKlines.stream()
                .map(kline -> new CandleData(
                    Long.parseLong(kline.get(0)),      // openTime
                    Double.parseDouble(kline.get(1)),  // open
                    Double.parseDouble(kline.get(2)),  // high
                    Double.parseDouble(kline.get(3)),  // low
                    Double.parseDouble(kline.get(4)),  // close
                    Double.parseDouble(kline.get(5))   // volume
                ))
                .collect(Collectors.toList());
        
        return new CandleResponseDto(symbol, interval, candles);
    }
    
    // Massive.io Aggregate(집계된 캔들 데이터)를 CandleResponseDto로 변환
    // Aggregate = Massive.io(Polygon.io) API 용어로 특정 기간의 OHLCV 데이터를 집계한 결과
    public static CandleResponseDto fromMassive(String ticker,
                                                 Integer multiplier,
                                                 String timespan,
                                                 MassiveAggregateResponse aggregates) {
        List<CandleData> candles = aggregates.getResults() != null
                ? aggregates.getResults().stream()
                    .map(result -> new CandleData(
                        result.getTimestamp() / 1_000_000, // 나노초를 밀리초로 변환
                        result.getOpenPrice(),
                        result.getHighPrice(),
                        result.getLowPrice(),
                        result.getClosePrice(),
                        result.getVolume() != null ? result.getVolume().doubleValue() : 0.0
                    ))
                    .collect(Collectors.toList())
                : Collections.emptyList();
        
        String interval = multiplier + " " + timespan;
        return new CandleResponseDto(ticker, interval, candles);
    }
}
