package com.curihous.qbit.api.domain.ai.service;

import com.curihous.qbit.api.domain.ai.dto.response.ReportTradeCycleResponseDto;
import com.curihous.qbit.domain.trade.entity.TradeExecution;
import com.curihous.qbit.domain.trade.service.TradeExecutionService;
import com.curihous.qbit.domain.tradecycle.entity.TradeCycle;
import com.curihous.qbit.domain.tradecycle.service.TradeCycleService;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.binance.service.BinanceMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDataService {
    
    private final TradeCycleService tradeCycleService;
    private final TradeExecutionService tradeExecutionService;
    private final BinanceMarketService binanceMarketService;
    
    // AI 서버용 특정 TradeCycle 데이터 조회
    public ReportTradeCycleResponseDto getTradeCycleForAi(Long tradeCycleId, String interval) {
        log.info("AI용 단일 TradeCycle 데이터 조회 시작: tradeCycleId={}, interval={}", 
                tradeCycleId, interval);
        
        TradeCycle tradeCycle = tradeCycleService.getTradeCycleById(tradeCycleId);
        
        // TODO: 보안을 위해 IP 화이트리스트 또는 API 키 기반 인증 추가 고려
        
        ReportTradeCycleResponseDto result = convertToAiDto(tradeCycle, interval);
        
        log.info("AI용 단일 TradeCycle 데이터 조회 완료: tradeCycleId={}", tradeCycleId);
        
        return result;
    }
    
    private ReportTradeCycleResponseDto convertToAiDto(TradeCycle tradeCycle, String interval) {
        String symbol = tradeCycle.getStock().getSymbol();
        String binanceSymbol = tradeCycle.getStock().getBinanceSymbol();
        
        // 1. 차트 데이터 조회 (Binance Kline) - Stock에 저장된 binanceSymbol 사용
        List<ReportTradeCycleResponseDto.CandleData> chartData = fetchChartData(
            binanceSymbol, 
            tradeCycle.getStartDate(), 
            tradeCycle.getEndDate(),
            interval
        );
        
        // 2. 매수/매도 지점 조회 (TradeExecution)
        List<ReportTradeCycleResponseDto.TradePoint> tradePoints = fetchTradePoints(
            tradeCycle.getUser(), 
            symbol, 
            tradeCycle.getStartDate(), 
            tradeCycle.getEndDate()
        );
        
        // 3. DTO 조합
        return ReportTradeCycleResponseDto.builder()
            .tradeCycleId(tradeCycle.getId())
            .symbol(symbol)
            .startDate(tradeCycle.getStartDate())
            .endDate(tradeCycle.getEndDate())
            .profitLossRate(tradeCycle.getProfitLossRate())
            .averageBuyPrice(tradeCycle.getAverageBuyPrice())
            .averageSellPrice(tradeCycle.getAverageSellPrice())
            .totalInvestmentAmount(tradeCycle.getTotalInvestmentAmount())
            .chartData(chartData)
            .tradePoints(tradePoints)
            .build();
    }
    
    // Binance에서 차트 데이터 조회
    private List<ReportTradeCycleResponseDto.CandleData> fetchChartData(
        String binanceSymbol, 
        java.time.LocalDateTime startDate, 
        java.time.LocalDateTime endDate,
        String interval
    ) {
        // Binance 심볼이 없으면 빈 리스트 반환
        if (binanceSymbol == null || binanceSymbol.isBlank()) {
            log.warn("Binance 심볼이 없어 차트 데이터를 조회할 수 없습니다.");
            return new ArrayList<>();
        }
        
        try {
            // LocalDateTime을 Unix timestamp (밀리초)로 변환
            Long startTime = startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            Long endTime = endDate != null ? 
                endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 
                null;
            
            // Binance에서 차트 데이터 조회 (AI 서버가 지정한 interval 사용)
            List<List<String>> klines = binanceMarketService.getKlines(
                binanceSymbol, 
                interval,
                startTime, 
                endTime,
                500  // limit
            );
            
            // Binance Kline 데이터를 DTO로 변환
            return klines.stream()
                .map(kline -> ReportTradeCycleResponseDto.CandleData.builder()
                    .timestamp(Long.parseLong(kline.get(0)))  // Open time(Unix timestamp)
                    .open(kline.get(1))
                    .high(kline.get(2))
                    .low(kline.get(3))
                    .close(kline.get(4))
                    .volume(kline.get(5))
                    .build())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("차트 데이터 조회 실패: binanceSymbol={}, error={}", binanceSymbol, e.getMessage());
            // 차트 데이터 조회 실패 시 빈 리스트 반환
            return new ArrayList<>();
        }
    }
    
    // TradeExecution에서 매수/매도 지점 조회
    private List<ReportTradeCycleResponseDto.TradePoint> fetchTradePoints(
        User user, 
        String symbol, 
        java.time.LocalDateTime startDate, 
        java.time.LocalDateTime endDate
    ) {
        List<TradeExecution> executions = tradeExecutionService.getTradeExecutionsByPeriod(
            user, 
            symbol, 
            startDate, 
            endDate
        );
        
        return executions.stream()
            .map(execution -> ReportTradeCycleResponseDto.TradePoint.builder()
                .timestamp(execution.getExecutedAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli())
                .side(execution.getOrderRequest().getSide().name())  // "BUY" or "SELL"
                .price(execution.getExecutedPrice())
                .quantity(execution.getExecutedQuantity())
                .build())
                .collect(Collectors.toList());
    }
}

