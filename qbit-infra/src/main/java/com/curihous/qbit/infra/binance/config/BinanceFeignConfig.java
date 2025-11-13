package com.curihous.qbit.infra.binance.config;

import feign.Request;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BinanceFeignConfig {
    
    private final ObjectFactory<HttpMessageConverters> messageConverters;

    // 요청 옵션 설정
    @Bean("binanceRequestOptions")
    public Request.Options requestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // 연결 타임아웃
            30, TimeUnit.SECONDS,  // 읽기 타임아웃
            true                   // 연결 유지
        );
    }
    
    // Binance 허용 파라미터만 whitelist로 필터링
    @Bean("binanceRequestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // binance-api 클라이언트에만 적용
            try {
                Object feignTarget = requestTemplate.feignTarget();
                if (feignTarget != null) {
                    String targetName = feignTarget.toString();
                    if (!targetName.contains("binance-api")) {
                        return;
                    }
                }
            } catch (Exception e) {
                // FeignTarget 정보를 가져올 수 없으면 필터링하지 않음
            }
            
            Set<String> allowed = Set.of("symbol", "interval", "startTime", "endTime", "limit");
            
            Map<String, Collection<String>> queries = new HashMap<>(requestTemplate.queries());
            Map<String, Collection<String>> filtered = new HashMap<>();
            
            queries.forEach((key, values) -> {
                if (key == null || key.isBlank() || !allowed.contains(key)) return;
                
                List<String> cleaned = values.stream()
                        .filter(v -> v != null && !"null".equalsIgnoreCase(v) && !v.isBlank())
                        .toList();
                
                if (!cleaned.isEmpty()) filtered.put(key, cleaned);
            });

            requestTemplate.queries(new HashMap<>());
            filtered.forEach(requestTemplate::query);
        };
    }

    @Bean("binanceErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new BinanceErrorDecoder();
    }
    
    @Bean("binanceDecoder")
    public Decoder feignDecoder() {
        return new ResponseEntityDecoder(new SpringDecoder(messageConverters));
    }
}