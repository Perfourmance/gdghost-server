package com.gdghost.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.ratelimit.RateLimiter;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

@Service
public class LoginService {

    static final int MAX_FAILURES_PER_LOGIN_ID = 5;
    static final int MAX_FAILURES_PER_IP = 50;
    static final Duration LOCK_WINDOW = Duration.ofMinutes(10);

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final LoginTokenService loginTokens;
    private final MeReader meReader;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    // 없는 아이디도 해시 비교를 한 번 해서 응답 시간으로 아이디 존재를 알 수 없게
    private final String dummyHash;

    public LoginService(MemberRepository members, PasswordEncoder passwordEncoder, LoginTokenService loginTokens,
            MeReader meReader, RateLimiter rateLimiter, Clock clock) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.loginTokens = loginTokens;
        this.meReader = meReader;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * 실패만 10분 동안 아이디당 5회 · IP당 50회까지 센다. 넘으면 비밀번호가 맞아도 429 LOGIN_LOCKED.
     * IP 상한은 아이디를 바꿔 가며 대입하는 것을 막는다 — 같은 와이파이의 여러 명을 위해 넉넉히.
     * 정지 계정은 비밀번호가 맞을 때만 403 — 틀리면 정지 여부도 알 수 없게.
     * @param previousToken 이 기기에 이미 있던 로그인 토큰 — 새로 로그인하면 지운다
     */
    @Transactional
    public LoginResult login(LoginRequest request, String userAgent, String clientIp, String previousToken) {
        String idKey = loginIdLockKey(request.loginId());
        String ipKey = "login:ip:" + clientIp;
        long locked = Math.max(rateLimiter.retryAfter(idKey, MAX_FAILURES_PER_LOGIN_ID),
                rateLimiter.retryAfter(ipKey, MAX_FAILURES_PER_IP));
        if (locked > 0) {
            throw new RateLimitedException(ErrorCode.LOGIN_LOCKED, locked);
        }

        // 탈퇴하면 login_id가 비워져 여기서 걸리지 않는다
        Optional<Member> found = members.findByLoginId(request.loginId().strip());
        boolean matches = passwordEncoder.matches(request.password(),
                found.map(Member::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches || found.get().getWithdrawnAt() != null) {
            rateLimiter.tryAcquire(idKey, MAX_FAILURES_PER_LOGIN_ID, LOCK_WINDOW);
            rateLimiter.tryAcquire(ipKey, MAX_FAILURES_PER_IP, LOCK_WINDOW);
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 맞지 않습니다.");
        }
        Member member = found.get();
        if (member.getSuspendedAt() != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, "이용이 정지된 계정입니다. 운영진에게 문의해 주세요.");
        }

        // 아이디 실패만 비운다 — IP 실패까지 비우면 자기 계정 로그인으로 대입 횟수를 되돌릴 수 있다
        rateLimiter.reset(idKey);
        if (previousToken != null) {
            loginTokens.revoke(previousToken);
        }
        member.markSeen(clock.instant());
        boolean remember = request.rememberOrDefault();
        String token = loginTokens.issue(member.getId(), remember, userAgent);
        return new LoginResult(token, remember, meReader.read(member));
    }

    static String loginIdLockKey(String loginId) {
        return "login:id:" + loginId.strip().toLowerCase(Locale.ROOT);
    }

}
