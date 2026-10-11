package com.gdghost.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void 창_안에서는_limit회까지만_허용하고_남은_시간을_알려준다() {
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isZero();
        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isZero();

        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1))).isEqualTo(40);
    }

    @Test
    void 창이_지나면_다시_센다() {
        limiter.tryAcquire("k", 1, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isPositive();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isZero();
    }

    @Test
    void 남은_시간은_올림해_최소_1초() {
        limiter.tryAcquire("k", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMillis(59_500));

        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isEqualTo(1);
    }

    static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-11T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

    }

}
