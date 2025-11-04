package com.curihous.qbit.infra.binance.config;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class BinanceErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String errorBody = null;
        try {
            if (response.body() != null) {
                byte[] bodyBytes = response.body().asInputStream().readAllBytes();
                errorBody = new String(bodyBytes, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Binance API 에러 응답 본문 읽기 실패", e);
        }
        
        log.error("Binance API 에러: method={}, status={}, reason={}, body={}", 
                 methodKey, response.status(), response.reason(), errorBody);
        
        return switch (response.status()) {
            case 400 -> new QbitException(ErrorCode.INVALID_INPUT_VALUE, "Binance API 요청 형식이 잘못되었습니다.");
            case 404 -> new QbitException(ErrorCode.STOCK_NOT_FOUND, "Binance에서 종목을 찾을 수 없습니다.");
            case 429 -> new QbitException(ErrorCode.EXTERNAL_API_ERROR, "Binance API 요청 한도 초과: 잠시 후 다시 시도해주세요");
            case 500, 503 -> new QbitException(ErrorCode.EXTERNAL_API_ERROR, "Binance API 서버 오류");
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }
}
