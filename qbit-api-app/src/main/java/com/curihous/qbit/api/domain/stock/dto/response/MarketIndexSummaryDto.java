package com.curihous.qbit.api.domain.stock.dto.response;

import com.curihous.qbit.domain.stock.entity.MarketIndex;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 해외 주요 지수 요약 DTO
 * 
 * 사용 API:
 * - GET /indices
 */
@Schema(description = "해외 주요 지수 요약 정보 (홈 화면용)")
public record MarketIndexSummaryDto(
        @Schema(description = "지수 심볼", example = "^GSPC")
        String symbol,

        @Schema(description = "지수명", example = "S&P 500")
        String name,

        @Schema(description = "현재 가격", example = "5911.69")
        BigDecimal currentPrice,

        @Schema(description = "변동 금액", example = "11.69")
        BigDecimal changeAmount,

        @Schema(description = "변동률(%)", example = "0.20")
        BigDecimal changePercentage,

        @Schema(description = "상승/하락 여부", example = "true")
        Boolean isPositive
) {
    public static MarketIndexSummaryDto fromEntity(MarketIndex marketIndex) {
        return new MarketIndexSummaryDto(
                marketIndex.getSymbol(),
                marketIndex.getName(),
                marketIndex.getCurrentPrice(),
                marketIndex.getChangeAmount(),
                marketIndex.getChangePercentage(),
                marketIndex.isPositive()
        );
    }
}

