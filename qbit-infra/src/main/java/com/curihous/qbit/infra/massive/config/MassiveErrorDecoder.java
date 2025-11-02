package com.curihous.qbit.infra.massive.config;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MassiveErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        log.error("Massive API 에러: method={}, status={}, reason={}", 
                methodKey, response.status(), response.reason());
        
        return switch (response.status()) {
            case 400 -> new QbitException(ErrorCode.INVALID_INPUT_VALUE, "Massive.io API 요청 형식이 잘못되었습니다.");
            case 404 -> new QbitException(ErrorCode.STOCK_NOT_FOUND, "Massive.io에서 종목을 찾을 수 없습니다.");
            case 429 -> new QbitException(ErrorCode.EXTERNAL_API_ERROR, "Massive.io API 요청 한도 초과: 잠시 후 다시 시도해주세요");
            case 500, 503 -> new QbitException(ErrorCode.EXTERNAL_API_ERROR, "Massive.io API 서버 오류");
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }
}

