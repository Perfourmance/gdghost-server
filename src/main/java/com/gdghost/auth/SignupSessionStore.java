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
 * signupToken 저장소. 서버 1대 전제라 메모리에 둔다 — 재시작하면 가입을 처음부터 다시 한다.
 * 만료는 처음 발급부터 30분 고정이고 clock으로 판단한다. 캐시 만료는 메모리 정리용.
 */
@Component
public class SignupSessionStore {

    static final Duration LIFETIME = Duration.ofMinutes(30);

    private final Clock clock;
    private final Cache<String, SignupSession> sessions = Caffeine.newBuilder()
            .expireAfterWrite(LIFETIME)
            .maximumSize(10_000)
            .build();

    public SignupSessionStore(Clock clock) {
        this.clock = clock;
    }

    public SignupSession open(long inviteCodeId, int generation) {
        SignupSession session = new SignupSession("st_" + TokenHasher.newToken(), inviteCodeId, generation, null, 0,
                clock.instant().plus(LIFETIME));
        sessions.put(session.token(), session);
        return session;
    }

    /** 없거나 30분이 지났으면 400 SIGNUP_SESSION_EXPIRED */
    public SignupSession get(String token) {
        SignupSession session = sessions.getIfPresent(token);
        if (session == null || !session.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.SIGNUP_SESSION_EXPIRED, "가입 시간이 지났습니다. 초대 코드부터 다시 입력해 주세요.");
        }
        return session;
    }

    public void recordEmailSent(String token) {
        sessions.asMap().computeIfPresent(token, (key, session) -> session.withEmailSent());
    }

    public void markVerified(String token, String email) {
        sessions.asMap().computeIfPresent(token, (key, session) -> session.withVerifiedEmail(email));
    }

    /** 가입 완료 뒤 — 같은 토큰으로 다시 가입하지 못하게 */
    public void close(String token) {
        sessions.invalidate(token);
    }

}
