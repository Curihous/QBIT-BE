package com.curihous.qbit.alpaca.config;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AlpacaClientConfig {

    @Value("${info.app.version}")
    private String appVersion;

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("User-Agent", "QBIT-Backend/" + appVersion);
            // 슬래시 디코딩 비활성화
            requestTemplate.decodeSlash(false);
        };
    }
}
