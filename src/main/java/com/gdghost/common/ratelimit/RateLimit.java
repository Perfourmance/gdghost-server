package com.gdghost.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * API 목록 비고의 '요청 제한'. 예: 부원당 분당 3회 → {@code @RateLimit(limit = 3)},
 * IP당 시간당 10회 → {@code @RateLimit(limit = 10, unit = ChronoUnit.HOURS, per = Per.IP)}.
 * 넘으면 429 RATE_LIMITED + Retry-After.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    int limit();

    long window() default 1;

    ChronoUnit unit() default ChronoUnit.MINUTES;

    Per per() default Per.MEMBER;

    enum Per {
        MEMBER, IP
    }

}
