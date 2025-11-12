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
        FieldQueryMapEncoder delegate = new FieldQueryMapEncoder();
        return object -> {
            if (object instanceof java.util.Map<?, ?> map) {
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                map.forEach((key, value) -> {
                    if (key != null) {
                        result.put(String.valueOf(key), value);
                    }
                });
                return result;
            }
            return delegate.encode(object);
        };
    }

    @Bean
    public Encoder feignEncoder() {
        return new SpringEncoder(messageConverters);
    }
}

