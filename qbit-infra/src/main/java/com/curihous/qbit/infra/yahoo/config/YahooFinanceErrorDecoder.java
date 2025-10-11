package com.curihous.qbit.infra.yahoo.config;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YahooFinanceErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {

        return switch (response.status()) {
            case 400 -> new QbitException(ErrorCode.INDEX_NOT_FOUND, "잘못된 지수 심볼입니다.");
            case 404 -> new QbitException(ErrorCode.INDEX_NOT_FOUND, "해당 지수를 찾을 수 없습니다.");
            case 429 -> new QbitException(ErrorCode.INDEX_DATA_UNAVAILABLE, "요청 한도 초과: 잠시 후 다시 시도해주세요");
            case 500, 503 -> new QbitException(ErrorCode.INDEX_DATA_UNAVAILABLE, "Yahoo Finance 서버 오류");
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }
}

