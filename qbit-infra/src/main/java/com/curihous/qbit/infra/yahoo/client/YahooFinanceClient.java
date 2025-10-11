package com.curihous.qbit.infra.yahoo.client;

import com.curihous.qbit.infra.yahoo.config.YahooFinanceConfig;
import com.curihous.qbit.infra.yahoo.dto.response.YahooFinanceChartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// Yahoo Finance API 클라이언트
@FeignClient(
    name = "yahoo-finance-client",
    url = "https://query1.finance.yahoo.com",
    configuration = YahooFinanceConfig.class
)
public interface YahooFinanceClient {

    // 지수 차트 데이터 조회
    @GetMapping("/v8/finance/chart/{symbol}")
    YahooFinanceChartResponse getChart(@PathVariable("symbol") String symbol);

    // 지수 과거 데이터 조회 (차트용)
    @GetMapping("/v8/finance/chart/{symbol}")
    YahooFinanceChartResponse getChartHistory(
        @PathVariable("symbol") String symbol,
        @RequestParam("interval") String interval,
        @RequestParam("period1") long period1,
        @RequestParam("period2") long period2
    );
}

