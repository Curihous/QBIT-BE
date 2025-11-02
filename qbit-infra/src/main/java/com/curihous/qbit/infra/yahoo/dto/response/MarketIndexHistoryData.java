package com.curihous.qbit.infra.yahoo.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 지수 과거 데이터 내부 DTO
 * 
 * Yahoo Finance API 응답을 변환한 Infra 레이어 내부 DTO
 * Controller에서는 이 DTO를 MarketIndexHistoryDto로 변환하여 프론트엔드에 응답
 * 
 * Yahoo Finance API: GET /v8/finance/chart/{symbol}
 * QBIT API: GET /indices/{symbol}/history 
 */
public record MarketIndexHistoryData(
    List<HistoryPoint> dataPoints
) {
    public record HistoryPoint(
        Long timestamp,    
        BigDecimal openPrice,
        BigDecimal closePrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        Long volume
    ) {}
}

