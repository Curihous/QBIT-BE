package com.curihous.qbit.infra.massive.service;

import com.curihous.qbit.infra.massive.client.MassiveClient;
import com.curihous.qbit.infra.massive.dto.response.MassiveAggregateResponse;
import com.curihous.qbit.infra.massive.dto.response.MassiveLastQuoteResponse;
import com.curihous.qbit.infra.massive.dto.response.MassiveTickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MassiveMarketService {

    private final MassiveClient massiveClient;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 전일 종가 조회
    @Cacheable(value = "massive-ticker")
    public MassiveTickerResponse getPreviousClose(String ticker) {
        log.debug("Massive 전일 종가 조회 시작: ticker={}", ticker);
        
        try {
            MassiveTickerResponse response = massiveClient.getPreviousClose(ticker);
            log.debug("Massive 전일 종가 조회 성공: ticker={}", ticker);
            return response;
        } catch (Exception e) {
            log.error("Massive 전일 종가 조회 실패: ticker={}, error={}", ticker, e.getMessage());
            throw e;
        }
    }

    // 집계 데이터 조회 (캔들/차트 데이터)
    @Cacheable(value = "massive-aggregate")
    public MassiveAggregateResponse getAggregates(String ticker, Integer multiplier, String timespan,
                                                   LocalDate from, LocalDate to, Boolean adjusted) {
        log.debug("Massive 집계 데이터 조회 시작: ticker={}, multiplier={}, timespan={}, from={}, to={}", 
                ticker, multiplier, timespan, from, to);
        
        try {
            String fromStr = from.format(DATE_FORMATTER);
            String toStr = to.format(DATE_FORMATTER);
            
            MassiveAggregateResponse response = massiveClient.getAggregates(
                    ticker, multiplier, timespan, fromStr, toStr, adjusted, "asc", 5000);
            log.debug("Massive 집계 데이터 조회 성공: ticker={}, count={}", ticker, 
                    response.getResults() != null ? response.getResults().size() : 0);
            return response;
        } catch (Exception e) {
            log.error("Massive 집계 데이터 조회 실패: ticker={}, error={}", ticker, e.getMessage());
            throw e;
        }
    }

    // 최근 호가 조회 (NBBO)
    @Cacheable(value = "massive-last-quote")
    public MassiveLastQuoteResponse getLastQuote(String ticker) {
        log.debug("Massive 최근 호가 조회 시작: ticker={}", ticker);
        
        try {
            MassiveLastQuoteResponse response = massiveClient.getLastQuote(ticker);
            log.debug("Massive 최근 호가 조회 성공: ticker={}", ticker);
            return response;
        } catch (Exception e) {
            log.error("Massive 최근 호가 조회 실패: ticker={}, error={}", ticker, e.getMessage());
            throw e;
        }
    }

}

