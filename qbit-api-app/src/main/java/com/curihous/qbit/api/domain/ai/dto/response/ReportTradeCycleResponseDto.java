package com.curihous.qbit.api.domain.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTradeCycleResponseDto {
    
    private Long tradeCycleId;
    private String symbol;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal profitLossRate;
    private BigDecimal averageBuyPrice;
    private BigDecimal averageSellPrice;
    private BigDecimal totalInvestmentAmount;

    // 차트 데이터 (OHLCV)
    private List<CandleData> chartData;
    
    // 매수/매도 실행 지점
    private List<TradePoint> tradePoints;
    
    // 캔들 데이터 (OHLCV)
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandleData {
        private Long timestamp;         
        private String open;
        private String high;
        private String low;
        private String close;
        private String volume;
    }
    
    // 매수/매도 실행 지점
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradePoint {
        private Long timestamp;           // Unix timestamp (밀리초)
        private String side;              // "BUY" or "SELL"
        private BigDecimal price;         // 체결가
        private BigDecimal quantity;      // 체결 수량
    }
}

