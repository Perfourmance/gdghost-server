package com.gdghost.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
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
 * 운영진 요청 이메일 변경 — 로그인도 메일도 안 되는 부원용. 개발자만, 본인 비밀번호를 다시 확인한 뒤에 쓴다.
 * 새 메일로 확인 링크를 보내고, 부원이 링크를 열어야 반영된다. 비밀번호는 바꾸지 않는다.
 */
@Service
public class StaffEmailChangeService {

    private final MemberRepository members;
    private final CurrentPasswords currentPasswords;
    private final EmailVerifier emailVerifier;
    private final EmailChangeRules rules;
    private final EmailChangeLogRepository changeLogs;
    private final OpsAuditLogger opsAudit;
    private final LoginTokenService loginTokens;
    private final MailService mailService;
    private final Clock clock;
    private final String webUrl;

    public StaffEmailChangeService(MemberRepository members, CurrentPasswords currentPasswords,
            EmailVerifier emailVerifier, EmailChangeRules rules, EmailChangeLogRepository changeLogs,
            OpsAuditLogger opsAudit, LoginTokenService loginTokens, MailService mailService, Clock clock,
            @Value("${web.url}") String webUrl) {
        this.members = members;
        this.currentPasswords = currentPasswords;
        this.emailVerifier = emailVerifier;
        this.rules = rules;
        this.changeLogs = changeLogs;
        this.opsAudit = opsAudit;
        this.loginTokens = loginTokens;
        this.mailService = mailService;
        this.clock = clock;
        this.webUrl = webUrl;
    }

    @Transactional
    public EmailChangeLinkSentResponse request(long staffId, long memberId, StaffEmailChangeRequest request) {
        Member staff = members.findById(staffId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        currentPasswords.check(staff, request.currentPassword());
        // 본인 메일은 본인 변경으로
        if (staffId == memberId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 이메일은 설정에서 바꿔 주세요.");
        }
        Member member = members.findById(memberId)
                .filter(found -> found.getWithdrawnAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        rules.checkInterval(member);
        String email = SignupService.normalize(request.newEmail());
        rules.checkNotTaken(member, email);

        Instant expiresAt = emailVerifier.sendLink(VerificationPurpose.EMAIL_CHANGE_LINK, email, memberId,
                token -> "운영진 요청으로 계정 이메일을 이 주소로 바꾸려고 합니다.\n"
                        + "아래 링크를 열면 바뀝니다 (24시간 유효).\n"
                        + webUrl + "/email-change/confirm?token=" + token + "\n"
                        + "요청한 적이 없다면 이 메일을 무시해 주세요.");
        // 이전 메일이 살아 있으면 링크가 열리기 전에 알 수 있게
        mailService.send(new Mail(member.getEmail(), "[GDGoC] 이메일 변경 요청 안내",
                "운영진이 계정 이메일 변경을 요청했습니다. 본인이 요청하지 않았다면 바로 운영진에게 알려 주세요."));
        changeLogs.save(EmailChangeLog.staff(memberId, member.getEmail(), email, staffId, clock.instant()));
        // 이메일 주소는 detail에 넣지 않는다 (email_change_log에 있음)
        opsAudit.record(OpsAudit.of(staffId, OpsAction.EMAIL_CHANGE_REQUEST)
                .targetMember(memberId)
                .detail(Map.of("identityNote", request.identityNote().strip())));
        return new EmailChangeLinkSentResponse(expiresAt);
    }

    /** 반영 후 모든 기기 로그아웃 — 부원은 새 메일로 비밀번호를 재설정한다 */
    // TODO 디스코드 기록(#45)
    @Transactional
    public void confirm(String token) {
        EmailVerification link = emailVerifier.useLink(VerificationPurpose.EMAIL_CHANGE_LINK, token);
        Member member = members.findById(link.getMemberId())
                .filter(found -> found.getWithdrawnAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID,
                        "링크가 만료됐거나 이미 사용됐습니다."));
        String email = link.getEmail();
        // 링크를 받은 뒤 다른 계정이 그 메일로 가입한 경우
        rules.checkNotTaken(member, email);

        Instant now = clock.instant();
        rules.change(member, email, now);
        changeLogs.findFirstByMemberIdAndNewEmailAndVerifiedAtIsNullOrderByIdDesc(member.getId(), email)
                .ifPresent(log -> log.verify(now));
        loginTokens.revokeAll(member.getId());
    }

}
