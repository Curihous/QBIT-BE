package com.curihous.qbit.infra.binance.service;

import com.curihous.qbit.infra.binance.client.BinanceClient;
import com.curihous.qbit.infra.binance.dto.response.BinanceKlineResponse;
import com.curihous.qbit.infra.binance.dto.response.BinanceOrderBookResponse;
import com.curihous.qbit.infra.binance.dto.response.BinanceTickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public List<List<String>> getKlines(String symbol, String interval, Long startTime, Long endTime) {
        log.info("Binance Kline 조회 시작: symbol={}, interval={}, startTime={}, endTime={}", 
                symbol, interval, startTime, endTime);
        
        try {
            List<List<String>> response = binanceClient.getKlines(symbol, interval, startTime, endTime, 500);
            log.info("Binance Kline 조회 성공: symbol={}, interval={}, count={}", 
                    symbol, interval, response.size());
            return response;
        } catch (Exception e) {
            log.error("Binance Kline 조회 실패: symbol={}, interval={}, error={}", 
                     symbol, interval, e.getMessage());
            throw e;
        }
    }

    // 호가창 조회 (1초 캐싱)
    // 사용 API: GET /api/v3/depth
    @Cacheable(value = "binance-orderbook")
    public BinanceOrderBookResponse getOrderBook(String symbol) {
        log.info("Binance 호가창 조회 시작: symbol={}", symbol);
        
        try {
            BinanceOrderBookResponse response = binanceClient.getOrderBook(symbol, 100);
            log.info("Binance 호가창 조회 성공: symbol={}", symbol);
            return response;
        } catch (Exception e) {
            log.error("Binance 호가창 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            throw e;
        }
    }
}
