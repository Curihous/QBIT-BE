package com.curihous.qbit.infra.binance.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Configuration
public class BinanceFeignConfig {

    // Feign 로깅 레벨 설정
    @Bean("binanceFeignLoggerLevel")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    // 요청 옵션 설정
    @Bean("binanceRequestOptions")
    public Request.Options requestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // 연결 타임아웃
            30, TimeUnit.SECONDS,  // 읽기 타임아웃
            true                   // 연결 유지
        );
    }
    // null 파라미터 제거 인터셉터
    // Binance API는 null 값이 쿼리 파라미터로 포함되면 오류를 발생시킴
    @Bean("binanceRequestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Map<String, Collection<String>> filtered = new HashMap<>();

            requestTemplate.queries().forEach((key, values) -> {
                if (values == null) return;
                // 값이 null이거나 문자열 "null"인 경우 제거
                List<String> cleaned = values.stream()
                        .filter(v -> v != null && !"null".equalsIgnoreCase(v))
                        .toList();
                if (!cleaned.isEmpty()) {
                    filtered.put(key, cleaned);
                }
            });

            // 기존 쿼리를 모두 초기화 후 새로 설정
            requestTemplate.queries(null);
            filtered.forEach(requestTemplate::query);
        };
    }

    @Bean("binanceErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new BinanceErrorDecoder();
    }
}