package com.gdghost.member;

import java.time.Duration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.ratelimit.RateLimiter;

/**
 * 로그인한 부원의 현재 비밀번호 확인 — 비밀번호 변경 · 이메일 변경 · 탈퇴가 같이 쓴다.
 * 틀린 것만 부원당 10분에 5회까지 두 API를 합쳐 센다 — 훔친 쿠키로 현재 비밀번호를 대입해 계정을 빼앗지 못하게.
 */
@Component
public class CurrentPasswords {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_WINDOW = Duration.ofMinutes(10);

    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    public CurrentPasswords(PasswordEncoder passwordEncoder, RateLimiter rateLimiter) {
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }

    /** 잠겼으면 맞아도 429 RATE_LIMITED, 틀리면 400 CURRENT_PASSWORD_WRONG */
    public void check(Member member, String currentPassword) {
        String lockKey = "current-password:" + member.getId();
        long locked = rateLimiter.retryAfter(lockKey, MAX_FAILURES);
        if (locked > 0) {
            throw new RateLimitedException(locked);
        }
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            rateLimiter.tryAcquire(lockKey, MAX_FAILURES, LOCK_WINDOW);
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_WRONG, "현재 비밀번호가 맞지 않습니다.");
        }
        rateLimiter.reset(lockKey);
    }

}
