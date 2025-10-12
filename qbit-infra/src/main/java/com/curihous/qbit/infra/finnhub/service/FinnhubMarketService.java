package com.curihous.qbit.infra.finnhub.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.infra.finnhub.client.FinnhubClient;
import com.curihous.qbit.infra.finnhub.dto.response.FinnhubQuoteResponse;
import com.curihous.qbit.infra.finnhub.dto.response.FinnhubCandleResponse;
import com.curihous.qbit.infra.finnhub.dto.response.FinnhubCryptoOrderBookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinnhubMarketService {

    private final FinnhubClient finnhubClient;
    
    @Value("${finnhub.api.key}")
    private String apiKey;

    // 실시간 시세 조회 (10초 캐싱)
    @Cacheable(value = "quote", key = "#symbol")
    public FinnhubQuoteResponse getQuote(String symbol) {
        try {
            log.info("Finnhub 시세 조회 시작: symbol={}", symbol);
            
            var finnhubQuote = finnhubClient.getQuote(symbol, apiKey);
            
            if (finnhubQuote.currentPrice() == null) {
                throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "시세 정보를 조회할 수 없습니다: " + symbol);
            }
            
            log.info("Finnhub 시세 조회 완료: symbol={}, price={}", symbol, finnhubQuote.currentPrice());
            
            return finnhubQuote;
            
        } catch (Exception e) {
            log.error("Finnhub 시세 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "시세 조회에 실패했습니다: " + e.getMessage());
        }
    }

    // 차트 데이터 조회 (5분 캐싱)
    @Cacheable(value = "candle", key = "#symbol + '_' + #resolution + '_' + #from + '_' + #to")
    public FinnhubCandleResponse getCandle(String symbol, String resolution, long from, long to) {
        try {
            log.info("Finnhub 차트 조회 시작: symbol={}, resolution={}, from={}, to={}", 
                    symbol, resolution, from, to);
            
            var finnhubCandle = finnhubClient.getCandle(symbol, resolution, from, to, apiKey);
            
            if (!"ok".equals(finnhubCandle.status())) {
                throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "차트 데이터를 조회할 수 없습니다: " + symbol);
            }
            
            log.info("Finnhub 차트 조회 완료: symbol={}, candles={}", 
                    symbol, finnhubCandle.timestamps() != null ? finnhubCandle.timestamps().size() : 0);
            
            return finnhubCandle;
            
        } catch (Exception e) {
            log.error("Finnhub 차트 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "차트 조회에 실패했습니다: " + e.getMessage());
        }
    }

    // 암호화폐 호가창 조회 (5초 캐싱)
    @Cacheable(value = "orderbook", key = "#symbol")
    public FinnhubCryptoOrderBookResponse getCryptoOrderBook(String symbol) {
        try {
            log.info("Finnhub 암호화폐 호가창 조회 시작: symbol={}", symbol);
            
            var finnhubOrderBook = finnhubClient.getCryptoOrderBook(symbol, apiKey);
            
            if (finnhubOrderBook.asks() == null || finnhubOrderBook.bids() == null) {
                throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "호가창 정보를 조회할 수 없습니다: " + symbol);
            }
            
            log.info("Finnhub 암호화폐 호가창 조회 완료: symbol={}, asks={}, bids={}", 
                    symbol, finnhubOrderBook.asks().size(), finnhubOrderBook.bids().size());
            
            return finnhubOrderBook;
            
        } catch (Exception e) {
            log.error("Finnhub 암호화폐 호가창 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            throw new QbitException(ErrorCode.EXTERNAL_API_ERROR, "호가창 조회에 실패했습니다: " + e.getMessage());
        }
    }
}
