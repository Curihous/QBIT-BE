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

            // 기존 쿼리를 복사 
            Map<String, Collection<String>> queries = new HashMap<>(requestTemplate.queries());

            // 불필요한 키 제거 (인증 파라미터)
            queries.remove("timestamp");
            queries.remove("signature");

            // null, 빈 값, "null" 문자열 제거
            Map<String, Collection<String>> filtered = new HashMap<>();
            queries.forEach((key, values) -> {
                // 키가 null이거나 빈 문자열이면 제거
                if (key == null || key.isBlank() || values == null) return;

                // 값이 null, 빈 문자열, "null" 문자열인 경우 제거
                List<String> cleaned = values.stream()
                        .filter(v -> v != null && !"null".equalsIgnoreCase(v) && !v.isBlank())
                        .toList();

                // 필터링된 값이 비어있지 않으면 추가
                if (!cleaned.isEmpty()) {
                    filtered.put(key, cleaned);
                }
            });

            // 완전히 교체
            requestTemplate.queries(new HashMap<>()); // 초기화
            filtered.forEach(requestTemplate::query);

            // 필터링 후 최종 URL 로그
            log.info("Binance Kline 요청 - 필터링 후 최종 URL: {}", requestTemplate.url());
            log.info("Binance Kline 요청 - 필터링 후 쿼리 파라미터: {}", filtered);
        };
    }

    @Bean("binanceErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new BinanceErrorDecoder();
    }
}