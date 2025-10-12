package com.curihous.qbit.infra.finnhub.client;

import com.curihous.qbit.infra.finnhub.config.FinnhubFeignConfig;
import com.curihous.qbit.infra.finnhub.dto.response.FinnhubQuoteResponse;
import com.curihous.qbit.infra.finnhub.dto.response.FinnhubCandleResponse;
import com.curihous.qbit.infra.finnhub.dto.response.FinnhubCryptoOrderBookResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Finnhub API 클라이언트
@FeignClient(
    name = "finnhub-client",
    url = "${finnhub.api.base-url}",
    configuration = FinnhubFeignConfig.class
)
public interface FinnhubClient {

    // 실시간 시세 조회
    @GetMapping("/quote")
    FinnhubQuoteResponse getQuote(
        @RequestParam("symbol") String symbol,
        @RequestParam("token") String token
    );

    // 차트 데이터 조회
    @GetMapping("/stock/candle")
    FinnhubCandleResponse getCandle(
        @RequestParam("symbol") String symbol,
        @RequestParam("resolution") String resolution,
        @RequestParam("from") long from,
        @RequestParam("to") long to,
        @RequestParam("token") String token
    );

    // 암호화폐 호가창 조회 (주식은 불가)
    @GetMapping("/crypto/orderbook")
    FinnhubCryptoOrderBookResponse getCryptoOrderBook(
        @RequestParam("symbol") String symbol,
        @RequestParam("token") String token
    );

}
