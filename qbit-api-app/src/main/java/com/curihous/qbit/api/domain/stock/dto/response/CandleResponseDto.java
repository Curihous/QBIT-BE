package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 암호화폐 차트 데이터 응답 DTO
 * 
 * QBIT API: GET /stocks/candle/{symbol}
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
    
    // 도메인 중심 팩토리 메서드 - 외부 API 구조와 분리
    public static CandleResponseDto of(String symbol, String interval, List<CandleData> candles) {
        return new CandleResponseDto(symbol, interval, candles);
    }
}
