package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 종목 랭킹 응답 DTO
 * 
 * QBIT API: GET /stocks/ranking/moving
 * QBIT API: GET /stocks/ranking/volume
 * QBIT API: GET /stocks/ranking/volatility
 */
@Builder
public record StockRankingResponseDto(
    @Schema(description = "종목 심볼", example = "AAPL")
    String symbol,
    
    @Schema(description = "종목명", example = "Apple Inc.")
    String stockName,
    
    @Schema(description = "현재가", example = "175.50")
    BigDecimal currentPrice,
    
    @Schema(description = "변화율 (%)", example = "5.32")
    BigDecimal changePercent
) {
}

