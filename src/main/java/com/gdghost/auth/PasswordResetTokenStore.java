package com.gdghost.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.TokenHasher;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 비밀번호 재설정 토큰(resetToken). 서버 메모리에 10분, 한 번만 쓴다 — 서버 1대 전제, 재시작하면 처음부터.
 */
@Component
public class PasswordResetTokenStore {

    static final Duration LIFETIME = Duration.ofMinutes(10);

    private final Clock clock;
    // 만료 판단은 clock으로 한다. 여기 설정은 메모리 정리용
    private final Cache<String, Entry> tokens = Caffeine.newBuilder()
            .expireAfterWrite(LIFETIME)
            .maximumSize(10_000)
            .build();

    public PasswordResetTokenStore(Clock clock) {
        this.clock = clock;
    }

    public record Entry(String token, long memberId, Instant expiresAt) {
    }

    public Entry issue(long memberId) {
        Entry entry = new Entry("rt_" + TokenHasher.newToken(), memberId, clock.instant().plus(LIFETIME));
        tokens.put(entry.token(), entry);
        return entry;
    }

    /** 쓰지 않고 확인만 — 비밀번호 규칙에 걸리면 같은 토큰으로 다시 시도할 수 있게 */
    public long memberId(String token) {
        Entry entry = tokens.getIfPresent(token);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            throw invalid();
        }
        return entry.memberId();
    }

    /** 동시에 두 번 써도 한 번만 성공하게 꺼내면서 지운다 */
    public void use(String token) {
        if (tokens.asMap().remove(token) == null) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.RESET_TOKEN_INVALID, "시간이 지났거나 이미 쓴 요청입니다. 인증번호부터 다시 받아 주세요.");
    }

}
