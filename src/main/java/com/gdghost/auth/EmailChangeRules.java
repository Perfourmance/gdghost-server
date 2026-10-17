package com.gdghost.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.mail.Mail;
import com.gdghost.common.mail.MailService;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;

/**
 * 본인 변경 · 운영진 요청이 같이 쓰는 규칙 — 30일에 한 번, 쓰고 있는 메일 불가, 바꾼 뒤 이전 메일로 안내.
 */
@Component
class EmailChangeRules {

    static final Duration CHANGE_INTERVAL = Duration.ofDays(30);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository members;
    private final MailService mailService;
    private final Clock clock;

    EmailChangeRules(MemberRepository members, MailService mailService, Clock clock) {
        this.members = members;
        this.mailService = mailService;
        this.clock = clock;
    }

    void checkInterval(Member member) {
        Instant changedAt = member.getEmailChangedAt();
        if (changedAt != null && clock.instant().isBefore(changedAt.plus(CHANGE_INTERVAL))) {
            LocalDate available = LocalDate.ofInstant(changedAt.plus(CHANGE_INTERVAL), KST);
            throw new BusinessException(ErrorCode.EMAIL_CHANGE_TOO_SOON,
                    "이메일은 30일에 한 번 바꿀 수 있습니다. " + available + "부터 다시 바꿀 수 있습니다.");
        }
    }

    void checkNotTaken(Member member, String email) {
        if (email.equals(member.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "지금 쓰는 이메일입니다.");
        }
        if (members.existsByEmail(email)) {
            throw taken();
        }
    }

    /** 트랜잭션 안에서. 이전 메일 안내는 커밋 후 발송 */
    void change(Member member, String email, Instant now) {
        String oldEmail = member.getEmail();
        member.changeEmail(email, now);
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw taken();
        }
        mailService.send(new Mail(oldEmail, "[GDGoC] 이메일 변경 안내",
                "계정 이메일이 바뀌었습니다. 본인이 바꾸지 않았다면 운영진에게 알려 주세요."));
    }

    private static BusinessException taken() {
        return new BusinessException(ErrorCode.EMAIL_ALREADY_USED, "이미 가입된 이메일입니다.");
    }

}
