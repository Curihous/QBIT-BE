package com.curihous.qbit.infra.binance.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
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
    
    // null 및 빈 파라미터 제거 인터셉터
    @Bean("binanceRequestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Map<String, Collection<String>> queries = new HashMap<>(requestTemplate.queries());

            // Binance 허용 파라미터만 whitelist로 남기기
            Set<String> allowed = Set.of("symbol", "interval", "startTime", "endTime", "limit");

            Map<String, Collection<String>> filtered = new HashMap<>();
            queries.forEach((key, values) -> {
                if (key == null || key.isBlank() || !allowed.contains(key)) return;

                List<String> cleaned = values.stream()
                        .filter(v -> v != null && !"null".equalsIgnoreCase(v) && !v.isBlank())
                        .toList();

                if (!cleaned.isEmpty()) filtered.put(key, cleaned);
            });

            // 교체
            requestTemplate.queries(new HashMap<>());
            filtered.forEach(requestTemplate::query);
            
            log.info("Binance 최종 요청 파라미터(whitelist 적용): {}", filtered);
            log.info("Binance Interceptor 후 쿼리 파라미터: {}", requestTemplate.queries());
            log.info("Binance Interceptor 후 URL: {}", requestTemplate.url());
        };
    }

    @Bean("binanceErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new BinanceErrorDecoder();
    }
}