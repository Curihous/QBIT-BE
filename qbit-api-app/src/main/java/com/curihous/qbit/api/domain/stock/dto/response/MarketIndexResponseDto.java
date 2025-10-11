package com.curihous.qbit.api.domain.stock.dto.response;

import com.curihous.qbit.domain.stock.entity.MarketIndex;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 해외 주요 지수 상세 DTO
 * 
 * 사용 API:
 * - GET /indices/{symbol}
 */
@Schema(description = "해외 주요 지수 상세 정보")
public record MarketIndexResponseDto(
        @Schema(description = "지수 심볼", example = "^GSPC")
        String symbol,

        @Schema(description = "지수명", example = "S&P 500")
        String name,

        @Schema(description = "지수 설명", example = "미국 S&P 500 주식 지수")
        String description,

        @Schema(description = "국가", example = "US")
        String country,

        @Schema(description = "통화", example = "USD")
        String currency,

        @Schema(description = "현재 가격", example = "5911.69")
        BigDecimal currentPrice,

        @Schema(description = "전일 종가", example = "5900.00")
        BigDecimal previousClose,

        @Schema(description = "변동 금액", example = "11.69")
        BigDecimal changeAmount,

        @Schema(description = "변동률(%)", example = "0.20")
        BigDecimal changePercentage,

        @Schema(description = "거래량", example = "50000000")
        Long volume,

        @Schema(description = "시가총액", example = "500000000000")
        Long marketCap,

        @Schema(description = "마지막 업데이트 시간", example = "2025-01-11T19:30:00")
        LocalDateTime lastUpdated,

        @Schema(description = "상승/하락 여부", example = "true")
        Boolean isPositive,

        @Schema(description = "활성화 여부", example = "true")
        Boolean isActive
) {
    public static MarketIndexResponseDto fromEntity(MarketIndex marketIndex) {
        return new MarketIndexResponseDto(
                marketIndex.getSymbol(),
                marketIndex.getName(),
                marketIndex.getDescription(),
                marketIndex.getCountry(),
                marketIndex.getCurrency(),
                marketIndex.getCurrentPrice(),
                marketIndex.getPreviousClose(),
                marketIndex.getChangeAmount(),
                marketIndex.getChangePercentage(),
                marketIndex.getVolume(),
                marketIndex.getMarketCap(),
                marketIndex.getLastUpdated(),
                marketIndex.isPositive(),
                marketIndex.getIsActive()
        );
    }
}

