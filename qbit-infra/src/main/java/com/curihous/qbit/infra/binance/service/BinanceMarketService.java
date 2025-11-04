package com.curihous.qbit.infra.binance.service;

import com.curihous.qbit.infra.binance.client.BinanceClient;
import com.curihous.qbit.infra.binance.dto.response.BinanceTickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceMarketService {

    private final BinanceClient binanceClient;

    // 24시간 통계 조회 (1초 캐싱)
    // 사용 API: GET /api/v3/ticker/24hr
    @Cacheable(value = "binance-ticker")
    public BinanceTickerResponse get24hrTicker(String symbol) {
        log.info("Binance 24시간 통계 조회 시작: symbol={}", symbol);
        
        try {
            BinanceTickerResponse response = binanceClient.get24hrTicker(symbol);
            log.info("Binance 24시간 통계 조회 성공: symbol={}", symbol);
            return response;
        } catch (Exception e) {
            log.error("Binance 24시간 통계 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            throw e;
        }
    }

    // Kline(캔들) 데이터 조회 (5분 캐싱)
    // 사용 API: GET /api/v3/klines
    @Cacheable(value = "binance-kline")
    public List<List<String>> getKlines(String symbol, String interval, Long startTime, Long endTime, Integer limit) {
        log.info("Binance Kline 조회 시작: symbol={}, interval={}, startTime={}, endTime={}, limit={}", 
                symbol, interval, startTime, endTime, limit);
        
        log.info("Binance Kline API 호출 URL 확인: symbol={}, interval={}, startTime={}, endTime={}, limit={}", 
                 symbol, interval, startTime, endTime, limit != null ? limit : 500);
        
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("symbol", symbol);
            params.put("interval", interval);
            if (startTime != null) params.put("startTime", startTime);
            if (endTime != null) params.put("endTime", endTime);
            params.put("limit", limit != null ? limit : 500);

            List<List<String>> response = binanceClient.getKlinesDynamic(params);
            log.info("Binance Kline 조회 성공: symbol={}, interval={}, count={}", 
                    symbol, interval, response.size());
            return response;
        } catch (Exception e) {
            log.error("Binance Kline 조회 실패: symbol={}, interval={}, error={}", 
                     symbol, interval, e.getMessage());
            throw e;
        }
    }

}
