package com.curihous.qbit.infra.binance.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.nio.charset.StandardCharsets;
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

    // 커스텀 Feign Logger 빈 등록 (실제 HTTP 요청/응답 상세 로깅)
    @Bean("binanceFeignLogger")
    public Logger feignLogger() {
        return new BinanceFeignLogger();
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
    @org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE) // 최우선실행
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Body 로깅 (디버깅용)
            if (requestTemplate.body() != null && requestTemplate.body().length > 0) {
                String bodyStr = new String(requestTemplate.body(), StandardCharsets.UTF_8);
                log.warn("Binance GET 요청에 body가 감지됨! body 길이: {}, 내용: {}", 
                        requestTemplate.body().length, bodyStr);
            }
            
            // Binance 허용 파라미터(whitelist)
            Set<String> allowed = Set.of("symbol", "interval", "startTime", "endTime", "limit");
            
            // blacklist
            Set<String> forbidden = Set.of("apikey", "apiKey", "api_key", "signature", "timestamp", "recvWindow");
            
            // 현재 쿼리 파라미터 가져오기
            Map<String, Collection<String>> queries = new HashMap<>(requestTemplate.queries());
            
            // 필터링 전 로그
            if (queries.containsKey("apikey") || queries.containsKey("apiKey") || queries.containsKey("api_key")) {
                log.warn("Binance 인터셉터 실행 전에 apikey 파라미터 감지: {}", queries.keySet());
            }
            
            Map<String, Collection<String>> filtered = new HashMap<>();
            queries.forEach((key, values) -> {
                // null, 빈 키 체크
                if (key == null || key.isBlank()) return;
                
                // Blacklist 체크
                if (forbidden.contains(key)) {
                    log.warn("Binance Market Data 엔드포인트에서 금지된 파라미터 제거: {}", key);
                    return;
                }
                
                // Whitelist 체크
                if (!allowed.contains(key)) {
                    log.debug("Binance 허용되지 않은 파라미터 제거: {}", key);
                    return;
                }

                // 값 정리
                List<String> cleaned = values.stream()
                        .filter(v -> v != null && !"null".equalsIgnoreCase(v) && !v.isBlank())
                        .toList();

                if (!cleaned.isEmpty()) filtered.put(key, cleaned);
            });

            // 쿼리 파라미터 교체
            requestTemplate.queries(new HashMap<>());
            filtered.forEach(requestTemplate::query);
        };
    }

    @Bean("binanceErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new BinanceErrorDecoder();
    }
}