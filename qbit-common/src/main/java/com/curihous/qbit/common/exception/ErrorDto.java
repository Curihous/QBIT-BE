package com.curihous.qbit.common.exception;

public record ErrorDto(
        String timestamp,
        int status,
        String errorCode,
        String message,
        String path
) {}
