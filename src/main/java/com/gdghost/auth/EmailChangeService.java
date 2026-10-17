package com.gdghost.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.mail.Mail;
import com.gdghost.common.mail.MailService;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.member.CurrentPasswords;
import com.gdghost.member.EmailChangeLog;
import com.gdghost.member.EmailChangeLogRepository;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/**
 * 본인 이메일 변경 — 현재 비밀번호 확인 후 새 메일로 인증번호, 번호가 맞으면 바꾼다.
 */
@Service
public class EmailChangeService {

    static final Duration CHANGE_INTERVAL = Duration.ofDays(30);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository members;
    private final CurrentPasswords currentPasswords;
    private final EmailVerifier emailVerifier;
    private final EmailChangeLogRepository changeLogs;
    private final LoginTokenService loginTokens;
    private final MailService mailService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public EmailChangeService(MemberRepository members, CurrentPasswords currentPasswords,
            EmailVerifier emailVerifier, EmailChangeLogRepository changeLogs, LoginTokenService loginTokens,
            MailService mailService, PlatformTransactionManager transactionManager, Clock clock) {
        this.members = members;
        this.currentPasswords = currentPasswords;
        this.emailVerifier = emailVerifier;
        this.changeLogs = changeLogs;
        this.loginTokens = loginTokens;
        this.mailService = mailService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public VerificationSentResponse sendCode(long memberId, EmailChangeCodeRequest request) {
        Member member = find(memberId);
        currentPasswords.check(member, request.currentPassword());
        checkInterval(member);
        String email = SignupService.normalize(request.newEmail());
        checkNotTaken(member, email);
        return emailVerifier.send(VerificationPurpose.EMAIL_CHANGE, email, memberId);
    }

    /**
     * 다른 기기는 모두 로그아웃, 이 기기는 새 토큰. 이전 메일로 변경 안내.
     * 번호 확인은 따로 커밋한다 — 틀린 횟수가 에러로 끝나도 남아야 해서.
     */
    public LoginTokenService.Issued confirm(long memberId, String currentToken, EmailChangeConfirmRequest request,
            String userAgent) {
        String email = SignupService.normalize(request.newEmail());
        emailVerifier.confirm(VerificationPurpose.EMAIL_CHANGE, email, request.code(), memberId);
        return transaction.execute(status -> change(memberId, email, currentToken, userAgent));
    }

    private LoginTokenService.Issued change(long memberId, String email, String currentToken, String userAgent) {
        Member member = find(memberId);
        // 번호를 받은 뒤 다른 계정이 그 메일로 가입했거나 · 다른 기기에서 먼저 바꾼 경우
        checkInterval(member);
        checkNotTaken(member, email);

        String oldEmail = member.getEmail();
        Instant now = clock.instant();
        member.changeEmail(email, now);
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw taken();
        }
        changeLogs.save(EmailChangeLog.self(memberId, oldEmail, email, now));
        mailService.send(new Mail(oldEmail, "[GDGoC] 이메일 변경 안내",
                "계정 이메일이 바뀌었습니다. 본인이 바꾸지 않았다면 운영진에게 알려 주세요."));
        return loginTokens.reissueOnly(memberId, currentToken, userAgent);
    }

    private Member find(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    private void checkInterval(Member member) {
        Instant changedAt = member.getEmailChangedAt();
        if (changedAt != null && clock.instant().isBefore(changedAt.plus(CHANGE_INTERVAL))) {
            LocalDate available = LocalDate.ofInstant(changedAt.plus(CHANGE_INTERVAL), KST);
            throw new BusinessException(ErrorCode.EMAIL_CHANGE_TOO_SOON,
                    "이메일은 30일에 한 번 바꿀 수 있습니다. " + available + "부터 다시 바꿀 수 있습니다.");
        }
    }

    private void checkNotTaken(Member member, String email) {
        if (email.equals(member.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "지금 쓰는 이메일입니다.");
        }
        if (members.existsByEmail(email)) {
            throw taken();
        }
    }

    private static BusinessException taken() {
        return new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "이미 가입된 이메일입니다.");
    }

}
