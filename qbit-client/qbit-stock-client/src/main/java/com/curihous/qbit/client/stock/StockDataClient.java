package com.curihous.qbit.client.stock;

import com.curihous.qbit.client.stock.dto.StockPriceResponse;
import com.curihous.qbit.client.stock.dto.StockInfoResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StockDataClient {
    
    /**
     * 주식 정보 조회
     */
    Mono<StockInfoResponse> getStockInfo(String ticker);
    
    /**
     * 실시간 주가 조회
     */
    Mono<StockPriceResponse> getCurrentPrice(String ticker);
    
    /**
     * 실시간 주가 스트림
     */
    Flux<StockPriceResponse> getRealtimePriceStream(String ticker);
    
    /**
     * 주식 목록 조회
     */
    Flux<StockInfoResponse> getStockList(String market);
}
