package com.curihous.qbit.api.domain.stock.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 지수 과거 데이터 DTO (차트용)
 * 
 * 사용 API:
 * - GET /indices/{symbol}/history
 */
@Schema(description = "지수 과거 데이터 (차트용)")
public record MarketIndexHistoryDto(
        @Schema(description = "타임스탬프 (밀리초)", example = "1704067200000")
        Long timestamp,

        @Schema(description = "시가", example = "4700.50")
        BigDecimal open,

        @Schema(description = "종가", example = "4750.20")
        BigDecimal close,

        @Schema(description = "고가", example = "4760.80")
        BigDecimal high,

        @Schema(description = "저가", example = "4690.30")
        BigDecimal low,

        @Schema(description = "거래량", example = "50000000")
        Long volume
) {
}

