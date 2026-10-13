package com.gdghost.common.ratelimit;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

public class RateLimitedException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        this(ErrorCode.RATE_LIMITED, retryAfterSeconds);
    }

    /** 429 업무 코드(INVITE_CODE_LOCKED 등)에도 Retry-After를 붙일 때 */
    public RateLimitedException(ErrorCode code, long retryAfterSeconds) {
        super(code, retryAfterSeconds + "초 뒤에 다시 시도해 주세요.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

}
