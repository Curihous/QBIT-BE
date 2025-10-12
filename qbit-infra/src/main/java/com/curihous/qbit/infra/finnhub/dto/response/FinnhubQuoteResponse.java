package com.curihous.qbit.infra.finnhub.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Finnhub 실시간 시세 응답 DTO
 * 
 * GET /quote?symbol=AAPL&token=YOUR_TOKEN
 */
public record FinnhubQuoteResponse(
    @JsonProperty("c")
    @Schema(description = "현재가", example = "172.25")
    Double currentPrice,
    
    @JsonProperty("h")
    @Schema(description = "당일 최고가", example = "173.15")
    Double highPrice,
    
    @JsonProperty("l")
    @Schema(description = "당일 최저가", example = "171.80")
    Double lowPrice,
    
    @JsonProperty("o")
    @Schema(description = "시작가", example = "172.00")
    Double openPrice,
    
    @JsonProperty("pc")
    @Schema(description = "전일 종가", example = "171.20")
    Double previousClose,
    
    @JsonProperty("d")
    @Schema(description = "전일 대비 변동가", example = "1.05")
    Double priceChange,
    
    @JsonProperty("dp")
    @Schema(description = "전일 대비 변동률", example = "0.61")
    Double priceChangePercentage
) {}
