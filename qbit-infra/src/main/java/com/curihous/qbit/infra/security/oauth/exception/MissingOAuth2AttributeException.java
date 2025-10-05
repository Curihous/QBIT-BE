package com.curihous.qbit.infra.security.oauth.exception;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;

public class MissingOAuth2AttributeException extends QbitException {
    public MissingOAuth2AttributeException(ErrorCode errorCode) {
        super(errorCode);
    }
}
