package com.curihous.qbit.infra.fmp.client;

import com.curihous.qbit.infra.alpaca.config.AlpacaClientConfig;
import com.curihous.qbit.infra.fmp.dto.FmpSp500ConstituentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@FeignClient(
    name = "financial-modeling-prep-client",
    url = "https://financialmodelingprep.com",
    configuration = AlpacaClientConfig.class 
)
public interface FinancialModelingPrepClient {

    // S&P500 구성 종목 조회
    @GetMapping("/api/v3/sp500_constituent")
    List<FmpSp500ConstituentResponse> getSp500Constituents(
        @RequestParam("apikey") String apiKey
    );
}


