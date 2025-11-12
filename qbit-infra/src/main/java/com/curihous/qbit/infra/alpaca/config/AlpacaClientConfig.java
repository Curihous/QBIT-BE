package com.curihous.qbit.infra.alpaca.config;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import feign.codec.Encoder;
import feign.querymap.BeanQueryMapEncoder;
import feign.QueryMapEncoder;

@Configuration
@RequiredArgsConstructor
public class AlpacaClientConfig {

    @Value("${info.app.version}")
    private String appVersion;

    @Bean("alpacaFeignLoggerLevel")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean("alpacaRequestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("User-Agent", "QBIT-Backend/" + appVersion);
            // 슬래시 디코딩 비활성화
            requestTemplate.decodeSlash(false);
        };
    }

    @Bean
    public Encoder feignEncoder() {
        return new Encoder.Default();
    }

    @Bean
    public QueryMapEncoder queryMapEncoder() {
        return new BeanQueryMapEncoder();
    }
}

