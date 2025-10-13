package com.curihous.qbit.infra.binance.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinanceErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("Binance API 호출 실패: methodKey={}, status={}, reason={}", 
                 methodKey, response.status(), response.reason());
        
        return defaultErrorDecoder.decode(methodKey, response);
    }
}
