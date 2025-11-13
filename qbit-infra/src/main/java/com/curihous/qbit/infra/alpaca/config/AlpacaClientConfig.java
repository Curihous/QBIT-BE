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

import java.util.*;

@Configuration
@RequiredArgsConstructor
public class AlpacaClientConfig {

    @Value("${info.app.version}")
    private String appVersion;

    @Bean("alpacaFeignLoggerLevel")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean("alpacaRequestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("User-Agent", "QBIT-Backend/" + appVersion);
            // 슬래시 디코딩 비활성화
            requestTemplate.decodeSlash(false);
            
            try {
                Object feignTarget = requestTemplate.feignTarget();
                if (feignTarget != null) {
                    String targetName = feignTarget.toString();
                    if (targetName.contains("AlpacaTradingClient") || targetName.contains("alpaca-trading-client")) {
                        // GET /orders API 허용 파라미터 whitelist
                        Set<String> allowed = Set.of("status", "limit", "after", "until", "direction", "nested", "side", "symbol");
                        
                        Map<String, Collection<String>> original = new HashMap<>(requestTemplate.queries());
                        Map<String, Collection<String>> filtered = new HashMap<>();
                        
                        original.forEach((key, values) -> {
                            if (key != null && allowed.contains(key)) {
                                // null 값 제거
                                List<String> cleaned = values.stream()
                                        .filter(v -> v != null && !"null".equalsIgnoreCase(v) && !v.isBlank())
                                        .toList();
                                if (!cleaned.isEmpty()) {
                                    filtered.put(key, cleaned);
                                }
                            }
                        });
                        
                        requestTemplate.queries(new HashMap<>());
                        filtered.forEach(requestTemplate::query);
                    }
                }
            } catch (Exception e) {
            }
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

