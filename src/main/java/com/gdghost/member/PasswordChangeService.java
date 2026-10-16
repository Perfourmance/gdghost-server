package com.gdghost.member;

import java.time.Clock;
import java.time.Duration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.ratelimit.RateLimiter;
import com.gdghost.common.security.LoginTokenService;

@Service
public class PasswordChangeService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_WINDOW = Duration.ofMinutes(10);

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final LoginTokenService loginTokens;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PasswordChangeService(MemberRepository members, PasswordEncoder passwordEncoder,
            LoginTokenService loginTokens, RateLimiter rateLimiter, Clock clock) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.loginTokens = loginTokens;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * 현재 비밀번호가 틀린 것만 10분에 5회까지 센다 — 훔친 쿠키로 현재 비밀번호를 대입해 계정을 빼앗지 못하게.
     * 다른 기기는 모두 로그아웃, 이 기기는 새 토큰.
     */
    @Transactional
    public LoginTokenService.Issued change(long memberId, String currentToken, PasswordChangeRequest request,
            String userAgent) {
        String lockKey = "password-change:" + memberId;
        long locked = rateLimiter.retryAfter(lockKey, MAX_FAILURES);
        if (locked > 0) {
            throw new RateLimitedException(locked);
        }

        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
            rateLimiter.tryAcquire(lockKey, MAX_FAILURES, LOCK_WINDOW);
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_WRONG, "현재 비밀번호가 맞지 않습니다.");
        }
        Passwords.check(request.newPassword());

        rateLimiter.reset(lockKey);
        member.changePassword(passwordEncoder.encode(request.newPassword()), clock.instant());
        return loginTokens.reissueOnly(memberId, currentToken, userAgent);
    }

}
