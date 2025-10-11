package com.curihous.qbit.infra.yahoo.config;

import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YahooFinanceConfig {

    @Bean
    public Logger.Level yahooFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options yahooRequestOptions() {
        return new Request.Options(
                10000,  // 연결 타임아웃 (밀리초)
                30000   // 읽기 타임아웃 (밀리초)
        );
    }

    @Bean
    public ErrorDecoder yahooErrorDecoder() {
        return new YahooFinanceErrorDecoder();
    }
}

