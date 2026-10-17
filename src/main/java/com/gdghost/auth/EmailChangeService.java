package com.gdghost.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
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

    private final MemberRepository members;
    private final CurrentPasswords currentPasswords;
    private final EmailVerifier emailVerifier;
    private final EmailChangeRules rules;
    private final EmailChangeLogRepository changeLogs;
    private final LoginTokenService loginTokens;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public EmailChangeService(MemberRepository members, CurrentPasswords currentPasswords,
            EmailVerifier emailVerifier, EmailChangeRules rules, EmailChangeLogRepository changeLogs,
            LoginTokenService loginTokens, PlatformTransactionManager transactionManager, Clock clock) {
        this.members = members;
        this.currentPasswords = currentPasswords;
        this.emailVerifier = emailVerifier;
        this.rules = rules;
        this.changeLogs = changeLogs;
        this.loginTokens = loginTokens;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public VerificationSentResponse sendCode(long memberId, EmailChangeCodeRequest request) {
        Member member = find(memberId);
        currentPasswords.check(member, request.currentPassword());
        rules.checkInterval(member);
        String email = SignupService.normalize(request.newEmail());
        rules.checkNotTaken(member, email);
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
        rules.checkInterval(member);
        rules.checkNotTaken(member, email);

        String oldEmail = member.getEmail();
        Instant now = clock.instant();
        rules.change(member, email, now);
        changeLogs.save(EmailChangeLog.self(memberId, oldEmail, email, now));
        return loginTokens.reissueOnly(memberId, currentToken, userAgent);
    }

    private Member find(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

}
