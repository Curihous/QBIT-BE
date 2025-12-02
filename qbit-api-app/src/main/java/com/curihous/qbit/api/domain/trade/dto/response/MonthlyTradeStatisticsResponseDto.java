package com.curihous.qbit.api.domain.trade.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 월별 거래 통계 응답 DTO
 * QBIT API: GET /trading/monthly
 */
public record MonthlyTradeStatisticsResponseDto(
    
    @Schema(description = "월별 통계 목록")
    List<MonthlyStatistics> statistics
) {
    
    public record MonthlyStatistics(
        
        @Schema(description = "연도", example = "2024")
        Integer year,
        
        @Schema(description = "월 (1-12)", example = "3")
        Integer month,
        
        @Schema(description = "총 거래 횟수 (체결된 주문 수)", example = "25")
        Long totalTradeCount,
        
        @Schema(description = "수익률 (%)", example = "5.25")
        BigDecimal profitRate,
        
        @Schema(description = "누적손익 (USD)", example = "1250.50")
        BigDecimal cumulativeProfitLoss
    ) {}
}

