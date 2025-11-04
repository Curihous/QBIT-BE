package com.curihous.qbit.infra.binance.client;

import com.curihous.qbit.infra.binance.config.BinanceFeignConfig;
import com.curihous.qbit.infra.binance.dto.response.BinanceTickerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

// Binance API Feign 클라이언트
@FeignClient(
    name = "binance-api",
    url = "https://api.binance.com/api/v3",
    configuration = BinanceFeignConfig.class
)
public interface BinanceClient {

    // 24시간 통계 조회
    @GetMapping("/ticker/24hr")
    BinanceTickerResponse get24hrTicker(@RequestParam("symbol") String symbol);

    // Kline(캔들) 데이터 조회
    @GetMapping("/klines")
    List<List<String>> getKlinesDynamic(@RequestParam Map<String, String> params);

}
