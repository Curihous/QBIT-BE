package com.curihous.qbit.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QbitException extends RuntimeException {
    private final ErrorCode errorCode;
}