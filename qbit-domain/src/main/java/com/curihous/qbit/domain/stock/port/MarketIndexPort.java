package com.curihous.qbit.domain.stock.port;

import com.curihous.qbit.domain.stock.entity.MarketIndex;

import java.math.BigDecimal;
import java.util.List;

/**
 * 시장 지수 데이터 조회를 위한 Port 인터페이스
 * (Hexagonal Architecture - Port)
 * 
 * 외부 시스템(Yahoo Finance 등)으로부터 지수 데이터를 가져오는 계약을 정의합니다.
 * 구체적인 구현은 Infra 계층의 Adapter에서 담당합니다.
 */
public interface MarketIndexPort {
    
    // 지수 조회
    MarketIndex getOrFetchIndex(String symbol);
    
    // 지수 과거 데이터 조회
    MarketIndexHistoryData getIndexHistory(String symbol, String from, String to);
    
    // 허용된 주요 지수 심볼 목록
    List<String> getMajorIndexSymbols();
    
    // 응집도 향상: Port와 그 계약에 사용되는 DTO를 함께 관리
    record MarketIndexHistoryData(
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
}

