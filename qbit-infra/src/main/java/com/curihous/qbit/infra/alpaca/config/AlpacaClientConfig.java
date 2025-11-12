package com.curihous.qbit.infra.alpaca.config;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import feign.codec.Encoder;
import feign.querymap.FieldQueryMapEncoder;
import feign.QueryMapEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;

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

    private final ObjectFactory<HttpMessageConverters> messageConverters;

    @Bean
    public QueryMapEncoder queryMapEncoder() {
        return new FieldQueryMapEncoder();
    }

    @Bean
    public Encoder feignEncoder() {
        return new SpringEncoder(messageConverters);
    }
}

