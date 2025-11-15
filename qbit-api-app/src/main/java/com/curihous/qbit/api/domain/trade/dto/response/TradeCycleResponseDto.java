package com.curihous.qbit.api.domain.trade.dto.response;

import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TradeCycle 조회 응답 DTO
 * 
 * QBIT API: GET /trading/trade-cycles
 */
public record TradeCycleResponseDto(
    @Schema(description = "TradeCycle ID", example = "123")
    Long tradeCycleId,
    
    @Schema(description = "종목 심볼", example = "AAPL")
    String symbol,
    
    @Schema(description = "종목 로고 이미지 URL", example = "https://logo.clearbit.com/apple.com")
    String logoUrl,
    
    @Schema(description = "사이클 시작일", example = "2024-01-01T09:00:00")
    LocalDateTime startDate,
    
    @Schema(description = "사이클 종료일", example = "2024-01-05T16:00:00")
    LocalDateTime endDate,
    
    @Schema(description = "손익률 (%)", example = "15.32")
    BigDecimal profitLossRate,
    
    @Schema(description = "손익 금액 (매도 금액 - 매수 금액)", example = "1532.00")
    BigDecimal profitLossAmount
) {
    public static TradeCycleResponseDto from(TradeCycle tradeCycle) {
        return new TradeCycleResponseDto(
            tradeCycle.getId(),
            tradeCycle.getStock() != null ? tradeCycle.getStock().getSymbol() : null,
            tradeCycle.getStock() != null ? tradeCycle.getStock().getLogoUrl() : null,
            tradeCycle.getStartDate(),
            tradeCycle.getEndDate(),
            tradeCycle.getProfitLossRate(),
            tradeCycle.getProfitLossAmount() != null ? tradeCycle.getProfitLossAmount() : BigDecimal.ZERO
        );
    }
}

