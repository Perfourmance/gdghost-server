package com.gdghost.common.error;

import java.util.List;

/**
 * 서비스가 규칙 위반을 알릴 때 던진다. 메시지는 사용자에게 보이는 detail이 된다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorField> errors;

    public BusinessException(ErrorCode code) {
        this(code, null, List.of());
    }

    public BusinessException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public BusinessException(ErrorCode code, String detail, List<ErrorField> errors) {
        super(detail != null ? detail : code.title());
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorField> errors() {
        return errors;
    }

}
