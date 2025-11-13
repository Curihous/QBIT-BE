package com.curihous.qbit.infra.massive.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MassiveFeignConfig {

    @Value("${massive.api-key}")
    private String apiKey;

    @Bean("massiveFeignLoggerLevel")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    // 요청 옵션 설정
    @Bean("massiveRequestOptions")
    public Request.Options requestOptions() {
        return new Request.Options(
            10, TimeUnit.SECONDS,  // 연결 타임아웃
            30, TimeUnit.SECONDS,  // 읽기 타임아웃
            true                   // 연결 유지
        );
    }

    // API 키 자동 추가 인터셉터
    @Bean("massiveRequestInterceptor")
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // massive-api 클라이언트에만 apikey 추가
            try {
                Object feignTarget = requestTemplate.feignTarget();
                if (feignTarget != null) {
                    String targetName = feignTarget.toString();
                    log.debug("Massive RequestInterceptor: method={}, url={}, feignTarget={}, queries={}", 
                            requestTemplate.method(), requestTemplate.url(), targetName, requestTemplate.queries());
                    if (targetName.contains("massive-api")) {
                        requestTemplate.query("apikey", apiKey);
                        log.debug("Massive API Key 추가됨: 최종 queries={}", requestTemplate.queries());
                    }
                }
            } catch (Exception e) {
                // FeignTarget 정보를 가져올 수 없으면 apikey 추가하지 않음
                log.debug("Massive RequestInterceptor: feignTarget 아님 - {}", e.getMessage());
            }
        };
    }

    @Bean("massiveErrorDecoder")
    public ErrorDecoder errorDecoder() {
        return new MassiveErrorDecoder();
    }
}

