package com.curihous.qbit.infra.massive.client;

import com.curihous.qbit.infra.massive.config.MassiveFeignConfig;
import com.curihous.qbit.infra.massive.dto.response.MassiveAggregateResponse;
import com.curihous.qbit.infra.massive.dto.response.MassiveLastQuoteResponse;
import com.curihous.qbit.infra.massive.dto.response.MassiveTickerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "massive-api",
    url = "https://api.massive.com/v2",
    configuration = MassiveFeignConfig.class
)
public interface MassiveClient {

    // 전일 종가 조회
    @GetMapping("/aggs/ticker/{ticker}/prev")
    MassiveTickerResponse getPreviousClose(@PathVariable("ticker") String ticker);

    // 집계 데이터 조회 (캔들/차트 데이터)
    @GetMapping("/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}")
    MassiveAggregateResponse getAggregates(
        @PathVariable("ticker") String ticker,
        @PathVariable("multiplier") Integer multiplier,
        @PathVariable("timespan") String timespan,
        @PathVariable("from") String from,
        @PathVariable("to") String to,
        @RequestParam(value = "adjusted", defaultValue = "true") Boolean adjusted,
        @RequestParam(value = "sort", defaultValue = "asc") String sort,
        @RequestParam(value = "limit", defaultValue = "5000") Integer limit
    );

    // 최근 호가 조회 (NBBO)
    @GetMapping("/last/nbbo/{ticker}")
    MassiveLastQuoteResponse getLastQuote(@PathVariable("ticker") String ticker);
}

