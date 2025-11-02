package com.curihous.qbit.infra.massive.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MassiveFeignConfig {

    @Value("${massive.api-key}")
    private String apiKey;

    @Bean("massiveFeignLoggerLevel")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    // 요청 옵션 설정
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // 연결 타임아웃
            30, TimeUnit.SECONDS,  // 읽기 타임아웃
            true                   // 연결 유지
        );
    }

    // API 키 자동 추가 인터셉터
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.query("apiKey", apiKey);
        };
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new MassiveErrorDecoder();
    }
}

