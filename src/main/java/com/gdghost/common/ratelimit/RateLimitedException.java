package com.gdghost.common.ratelimit;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

public class RateLimitedException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, retryAfterSeconds + "초 뒤에 다시 시도해 주세요.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

}
