package com.gdghost.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.ratelimit.RateLimiter;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.member.Passwords;

/**
 * 비밀번호 재설정. 가입 여부를 알리지 않으려고 가입 안 된 메일도 같은 응답 · 같은 제한을 받는다.
 */
@Service
public class PasswordResetService {

    private final MemberRepository members;
    private final EmailVerifier emailVerifier;
    private final PasswordResetTokenStore resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final LoginTokenService loginTokens;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PasswordResetService(MemberRepository members, EmailVerifier emailVerifier,
            PasswordResetTokenStore resetTokens, PasswordEncoder passwordEncoder, LoginTokenService loginTokens,
            RateLimiter rateLimiter, Clock clock) {
        this.members = members;
        this.emailVerifier = emailVerifier;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.loginTokens = loginTokens;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** 가입 안 된 메일은 기록만 남기고 보내지 않는다 — 재발송 대기 · 하루 5회가 똑같이 걸리게 */
    public PasswordResetCodeSentResponse sendCode(String rawEmail) {
        String email = SignupService.normalize(rawEmail);
        Optional<Member> member = members.findByEmail(email);
        VerificationSentResponse sent = member.isPresent()
                ? emailVerifier.send(VerificationPurpose.RESET, email, member.get().getId())
                : emailVerifier.recordWithoutSending(VerificationPurpose.RESET, email);
        return new PasswordResetCodeSentResponse(sent.resendAvailableAt());
    }

    /** 가입 안 된 메일은 번호가 맞아도 INVALID — 틀린 횟수는 똑같이 센다 */
    public PasswordResetTokenResponse verify(String rawEmail, String code) {
        String email = SignupService.normalize(rawEmail);
        emailVerifier.confirm(VerificationPurpose.RESET, email, code);
        Member member = members.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "인증번호가 맞지 않습니다."));
        PasswordResetTokenStore.Entry token = resetTokens.issue(member.getId());
        return new PasswordResetTokenResponse(token.token(), token.expiresAt());
    }

    /** 모든 기기를 로그아웃한다. 자동 로그인은 하지 않음 */
    @Transactional
    public void reset(String resetToken, String newPassword) {
        long memberId = resetTokens.memberId(resetToken);
        Passwords.check(newPassword);
        resetTokens.use(resetToken);

        Member member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID));
        Instant now = clock.instant();
        member.changePassword(passwordEncoder.encode(newPassword), now);
        loginTokens.revokeAll(memberId);
        // 잠긴 아이디도 새 비밀번호로 바로 로그인할 수 있게
        rateLimiter.reset(LoginService.loginIdLockKey(member.getLoginId()));
    }

}
