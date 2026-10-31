package com.gdghost.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * API 목록 비고의 '요청 제한'. 예: 부원당 분당 3회 → {@code @RateLimit(limit = 3)},
 * IP당 시간당 10회 → {@code @RateLimit(limit = 10, unit = ChronoUnit.HOURS, per = Per.IP)}.
 * 넘으면 429 RATE_LIMITED + Retry-After. 여러 개 붙일 수 있고 위에서부터 센다 — 짧은 창을 먼저 쓴다
 * (짧은 창에서 막힌 요청이 긴 창의 횟수를 쓰지 않게).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.List.class)
public @interface RateLimit {

    int limit();

    long window() default 1;

    ChronoUnit unit() default ChronoUnit.MINUTES;

    Per per() default Per.MEMBER;

    enum Per {
        MEMBER, IP
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        RateLimit[] value();
    }

}
