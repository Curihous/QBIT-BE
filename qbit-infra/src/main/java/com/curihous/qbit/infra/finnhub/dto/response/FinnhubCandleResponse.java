package com.curihous.qbit.infra.finnhub.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Finnhub 차트 데이터(OHLCV) 응답 DTO
 * 
 * GET /stock/candle?symbol=AAPL&resolution=1&from=1696800000&to=1696886400&token=YOUR_TOKEN
 */
public record FinnhubCandleResponse(
    @JsonProperty("s")
    @Schema(description = "상태 (Status)", example = "ok")
    String status,
    
    @JsonProperty("c")
    @Schema(description = "종가 배열 (Close Prices)", example = "[172.25, 172.30, 172.15]")
    List<Double> closePrices,
    
    @JsonProperty("h")
    @Schema(description = "고가 배열 (High Prices)", example = "[172.50, 172.45, 172.40]")
    List<Double> highPrices,
    
    @JsonProperty("l")
    @Schema(description = "저가 배열 (Low Prices)", example = "[172.00, 172.10, 172.05]")
    List<Double> lowPrices,
    
    @JsonProperty("o")
    @Schema(description = "시가 배열 (Open Prices)", example = "[172.10, 172.20, 172.25]")
    List<Double> openPrices,
    
    @JsonProperty("v")
    @Schema(description = "거래량 배열 (Volumes)", example = "[1000000, 1200000, 950000]")
    List<Long> volumes,
    
    @JsonProperty("t")
    @Schema(description = "타임스탬프 배열 (Timestamps)", example = "[1696800000, 1696800060, 1696800120]")
    List<Long> timestamps
) {}
