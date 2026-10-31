package com.gdghost.upload;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 열람 주소의 서명 시각 · 만료. 정각으로 맞춰 같은 시간대에는 같은 주소가 나오게 한다(브라우저 캐시).
 * 다음 정각까지 10분 미만이면 너무 짧으므로 그다음 정각까지. 서명 시각은 항상 지난 정각이다.
 */
record PhotoUrlWindow(Instant signedAt, Instant expiresAt) {

    private static final Duration MIN_REMAINING = Duration.ofMinutes(10);

    static PhotoUrlWindow at(Instant now) {
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        Instant next = hour.plus(1, ChronoUnit.HOURS);
        boolean tooShort = Duration.between(now, next).compareTo(MIN_REMAINING) < 0;
        return new PhotoUrlWindow(hour, tooShort ? next.plus(1, ChronoUnit.HOURS) : next);
    }

    Duration ttl() {
        return Duration.between(signedAt, expiresAt);
    }

}
