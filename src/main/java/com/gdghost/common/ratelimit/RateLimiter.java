package com.gdghost.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 고정 창(첫 요청부터 window 동안 limit회). 서버 1대 전제라 메모리에 센다.
 */
@Component
public class RateLimiter {

    private final Clock clock;
    // 가장 긴 창보다 길게만 두면 된다. 지난 창은 아래 compute에서 새로 시작하므로 정확성과 무관
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofDays(1))
            .maximumSize(100_000)
            .build();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    private record Window(Instant end, int count) {
    }

    /**
     * @return 허용이면 0, 넘었으면 다시 시도할 수 있을 때까지의 초(Retry-After)
     */
    public long tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        AtomicLong retryAfter = new AtomicLong();
        windows.asMap().compute(key, (k, current) -> {
            Window active = current == null || !now.isBefore(current.end()) ? new Window(now.plus(window), 0) : current;
            if (active.count() >= limit) {
                retryAfter.set(ceilSeconds(Duration.between(now, active.end())));
                return active;
            }
            return new Window(active.end(), active.count() + 1);
        });
        return retryAfter.get();
    }

    /**
     * 세지 않고 확인만 한다 — 실패만 세는 잠금(초대 코드 5회 실패 등)에서 시도 전에 부른다.
     * @return 막혔으면 남은 초, 아니면 0
     */
    public long retryAfter(String key, int limit) {
        Instant now = clock.instant();
        Window current = windows.getIfPresent(key);
        if (current == null || !now.isBefore(current.end()) || current.count() < limit) {
            return 0;
        }
        return ceilSeconds(Duration.between(now, current.end()));
    }

    /** 실패만 세는 잠금에서 성공하면 비운다 */
    public void reset(String key) {
        windows.invalidate(key);
    }

    private static long ceilSeconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }

}
