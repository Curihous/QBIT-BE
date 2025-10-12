package com.curihous.qbit.infra.finnhub.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinnhubFeignConfig {

    @Bean
    public Logger.Level finnhubFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor finnhubRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("User-Agent", "QBIT-Trading-App/1.0");
        };
    }
}
