package com.curihous.qbit.common.exception;

import lombok.Getter;

@Getter
public class QbitException extends RuntimeException {
    private final ErrorCode errorCode;
    
    public QbitException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public QbitException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public QbitException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}