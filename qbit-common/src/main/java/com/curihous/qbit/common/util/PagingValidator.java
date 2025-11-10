package com.curihous.qbit.common.util;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;

public final class PagingValidator {

    private PagingValidator() {
    }

    public static void validate(int page, int size) {
        validate(page, size, 100);
    }

    public static void validate(int page, int size, int maxSize) {
        if (page < 0) {
            throw new QbitException(ErrorCode.INVALID_INPUT_VALUE, "page 파라미터는 0 이상이어야 합니다.");
        }

        if (size <= 0 || size > maxSize) {
            throw new QbitException(ErrorCode.INVALID_INPUT_VALUE, "size 파라미터는 1 이상 " + maxSize + " 이하이어야 합니다.");
        }
    }
}

