package com.curihous.qbit.infra.alpaca.client;

import com.curihous.qbit.infra.alpaca.config.AlpacaClientConfig;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaBarsResponse;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaMoversResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "alpaca-data-client",
    url = "https://data.alpaca.markets",
    configuration = AlpacaClientConfig.class
)
public interface AlpacaDataClient {

    // Movers API - 당일 상승률/하락률 상위 종목 조회
    @GetMapping("/v1beta1/screener/stocks/movers")
    AlpacaMoversResponse getMovers(
        @RequestHeader("Authorization") String authorization
    );

    // Bars API - 종목의 캔들 데이터 조회
    @GetMapping("/v2/stocks/{symbol}/bars")
    AlpacaBarsResponse getBars(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("symbol") String symbol,
        @RequestParam(value = "timeframe", required = false) String timeframe,
        @RequestParam(value = "start", required = false) String start,
        @RequestParam(value = "end", required = false) String end,
        @RequestParam(value = "limit", required = false) Integer limit
    );
}

