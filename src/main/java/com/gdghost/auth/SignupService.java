package com.gdghost.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.ratelimit.RateLimiter;
import com.gdghost.invite.InviteCode;
import com.gdghost.invite.InviteCodeRepository;
import com.gdghost.invite.InviteCodeState;
import com.gdghost.member.MemberRepository;

@Service
public class SignupService {

    static final int INVITE_CODE_MAX_FAILURES_PER_DEVICE = 5;
    static final int INVITE_CODE_MAX_FAILURES_PER_IP = 50;
    static final Duration INVITE_CODE_LOCK_WINDOW = Duration.ofMinutes(10);
    static final int MAX_EMAIL_SENDS_PER_SESSION = 5;

    private final InviteCodeRepository inviteCodes;
    private final MemberRepository members;
    private final SignupSessionStore sessions;
    private final EmailVerifier emailVerifier;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public SignupService(InviteCodeRepository inviteCodes, MemberRepository members, SignupSessionStore sessions,
            EmailVerifier emailVerifier, RateLimiter rateLimiter, Clock clock) {
        this.inviteCodes = inviteCodes;
        this.members = members;
        this.sessions = sessions;
        this.emailVerifier = emailVerifier;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * 실패만 10분 동안 기기당 5회 · IP당 50회까지 센다. 둘 중 하나라도 넘으면 맞는 코드도 429.
     * IP 상한은 같은 와이파이의 여러 명을 위해 넉넉히, 쿠키를 버리며 대입하는 것만 막는다.
     * 없음 · 폐기 · 만료 · 소진은 같은 에러 — 어떤 코드가 있었는지 알 수 없게.
     * used_count는 가입 완료 때 늘린다.
     */
    public InviteCodeCheckResponse checkInviteCode(String code, String deviceId, String clientIp) {
        String deviceKey = deviceLockKey(deviceId);
        String ipKey = ipLockKey(clientIp);
        long locked = Math.max(rateLimiter.retryAfter(deviceKey, INVITE_CODE_MAX_FAILURES_PER_DEVICE),
                rateLimiter.retryAfter(ipKey, INVITE_CODE_MAX_FAILURES_PER_IP));
        if (locked > 0) {
            throw new RateLimitedException(ErrorCode.INVITE_CODE_LOCKED, locked);
        }
        InviteCode inviteCode = inviteCodes.findByCode(code.strip().toUpperCase(Locale.ROOT))
                .filter(found -> found.state(clock.instant()) == InviteCodeState.ACTIVE)
                .orElseThrow(() -> {
                    rateLimiter.tryAcquire(deviceKey, INVITE_CODE_MAX_FAILURES_PER_DEVICE, INVITE_CODE_LOCK_WINDOW);
                    rateLimiter.tryAcquire(ipKey, INVITE_CODE_MAX_FAILURES_PER_IP, INVITE_CODE_LOCK_WINDOW);
                    return new BusinessException(ErrorCode.INVITE_CODE_INVALID, "쓸 수 없는 초대 코드입니다.");
                });

        SignupSession session = sessions.open(inviteCode.getId(), inviteCode.getGeneration());
        return new InviteCodeCheckResponse(session.token(), session.generation(), session.expiresAt());
    }

    /**
     * 메일별 제한(60초 · 하루 5회)은 주소를 바꾸면 피할 수 있어 토큰당 5통을 따로 센다.
     * 실제로 보낸 것만 센다 — 재발송 대기 · 한도 초과로 거절된 요청은 넣지 않음.
     */
    public VerificationSentResponse sendEmailVerification(SignupEmailRequest request) {
        SignupSession session = sessions.get(request.signupToken());
        if (session.emailSendCount() >= MAX_EMAIL_SENDS_PER_SESSION) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "이번 가입에서는 인증 메일을 더 보낼 수 없습니다. 초대 코드부터 다시 시작해 주세요.");
        }
        String email = normalize(request.email());
        if (members.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "이미 가입된 이메일입니다.");
        }
        VerificationSentResponse sent = emailVerifier.send(VerificationPurpose.SIGNUP, email, null);
        sessions.recordEmailSent(request.signupToken());
        return sent;
    }

    public EmailVerifiedResponse confirmEmailVerification(SignupEmailConfirmRequest request) {
        sessions.get(request.signupToken());
        String email = normalize(request.email());
        emailVerifier.confirm(VerificationPurpose.SIGNUP, email, request.code());
        sessions.markVerified(request.signupToken(), email);
        return new EmailVerifiedResponse(true);
    }

    static String deviceLockKey(String deviceId) {
        return "signup-invite-code|device:" + deviceId;
    }

    static String ipLockKey(String clientIp) {
        return "signup-invite-code|ip:" + clientIp;
    }

    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

}
