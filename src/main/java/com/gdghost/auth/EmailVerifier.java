package com.gdghost.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.mail.Mail;
import com.gdghost.common.mail.MailService;
import com.gdghost.common.ratelimit.RateLimitedException;
import com.gdghost.common.security.TokenHasher;

/**
 * 메일 인증번호 6자리 — 가입 · 비밀번호 재설정 · 이메일 변경이 같이 쓴다.
 * 같은 메일 · 용도에서는 가장 최근 번호만 유효하다 (다시 보내면 이전 번호는 무효).
 * 아이디 안내처럼 번호 없는 메일도 같은 표에 남겨 발송 제한을 같이 센다.
 * 확인 링크도 같은 표에 토큰 해시로 남긴다.
 */
@Component
public class EmailVerifier {

    static final Duration LIFETIME = Duration.ofMinutes(10);
    static final Duration LINK_LIFETIME = Duration.ofHours(24);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    static final int DAILY_LIMIT = 5;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final RandomGenerator RANDOM = new SecureRandom();

    private final EmailVerificationRepository verifications;
    private final MailService mailService;
    private final Clock clock;

    public EmailVerifier(EmailVerificationRepository verifications, MailService mailService, Clock clock) {
        this.verifications = verifications;
        this.mailService = mailService;
        this.clock = clock;
    }

    /** email은 소문자로 정규화된 값 */
    @Transactional
    public VerificationSentResponse send(VerificationPurpose purpose, String email, Long memberId) {
        return issue(purpose, email, memberId, true);
    }

    /**
     * 같은 제한(재발송 대기 · 하루 5회)으로 기록만 남기고 메일은 보내지 않는다.
     * 비밀번호 재설정에서 가입 안 된 메일도 가입된 메일과 똑같이 응답하려고 쓴다.
     */
    @Transactional
    public VerificationSentResponse recordWithoutSending(VerificationPurpose purpose, String email) {
        return issue(purpose, email, null, false);
    }

    /**
     * 번호 없는 안내 메일. 제한은 {@link VerificationPurpose#limitGroup()}의 인증번호와 합산한다.
     * text가 null이면 기록만 남기고 보내지 않는다 (가입 안 된 메일).
     */
    @Transactional
    public void sendNotice(VerificationPurpose purpose, String email, Long memberId, String text) {
        Instant now = clock.instant();
        checkLimits(purpose, email, now);
        // code_hash는 NOT NULL — 아무도 모르는 값을 넣어 확인에 쓰일 수 없게
        verifications.save(EmailVerification.create(email, memberId, purpose,
                TokenHasher.sha256(TokenHasher.newToken()), now, now));
        if (text != null) {
            mailService.send(new Mail(email, "[GDGoC] " + purpose.mailSubject(), text));
        }
    }

    /**
     * 확인 링크 메일. 토큰은 해시로만 남기고 24시간 유효.
     * @param text 토큰을 받아 메일 본문을 만든다
     * @return 만료 시각
     */
    @Transactional
    public Instant sendLink(VerificationPurpose purpose, String email, long memberId, Function<String, String> text) {
        Instant now = clock.instant();
        checkLimits(purpose, email, now);

        String token = TokenHasher.newToken();
        Instant expiresAt = now.plus(LINK_LIFETIME);
        verifications.save(EmailVerification.create(email, memberId, purpose, TokenHasher.sha256(token), expiresAt,
                now));
        mailService.send(new Mail(email, "[GDGoC] " + purpose.mailSubject(), text.apply(token)));
        return expiresAt;
    }

    /**
     * 링크는 한 번만 쓸 수 있고, 그 부원이 받은 가장 최근 링크만 유효하다.
     * 감싸는 트랜잭션이 롤백되면 쓴 표시도 롤백돼 다시 쓸 수 있다.
     */
    @Transactional
    public EmailVerification useLink(VerificationPurpose purpose, String token) {
        Instant now = clock.instant();
        EmailVerification link = verifications.findByCodeHashAndPurpose(TokenHasher.sha256(token), purpose)
                .filter(found -> found.getVerifiedAt() == null && !found.isExpired(now))
                .filter(found -> isLatest(found, purpose))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESET_TOKEN_INVALID,
                        "링크가 만료됐거나 이미 사용됐습니다."));
        link.markVerified(now);
        return link;
    }

    private boolean isLatest(EmailVerification link, VerificationPurpose purpose) {
        return verifications.findFirstByMemberIdAndPurposeOrderByIdDesc(link.getMemberId(), purpose)
                .filter(latest -> latest.getId().equals(link.getId()))
                .isPresent();
    }

    private VerificationSentResponse issue(VerificationPurpose purpose, String email, Long memberId,
            boolean deliver) {
        Instant now = clock.instant();
        checkLimits(purpose, email, now);

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant expiresAt = now.plus(LIFETIME);
        verifications.save(EmailVerification.create(email, memberId, purpose, TokenHasher.sha256(code), expiresAt,
                now));
        if (deliver) {
            // 한도를 넘으면 503이 나고 위 저장도 롤백된다. 발송은 커밋 후
            mailService.send(new Mail(email, "[GDGoC] " + purpose.mailSubject(),
                    "인증번호: " + code + "\n10분 안에 입력해 주세요."));
        }
        return new VerificationSentResponse(expiresAt, now.plus(RESEND_AFTER));
    }

    private void checkLimits(VerificationPurpose purpose, String email, Instant now) {
        verifications.findFirstByEmailAndPurposeInOrderByIdDesc(email, purpose.limitGroup())
                .map(last -> Duration.between(now, last.getCreatedAt().plus(RESEND_AFTER)))
                .filter(wait -> wait.isPositive())
                .ifPresent(wait -> {
                    throw new RateLimitedException(ErrorCode.EMAIL_RESEND_TOO_SOON, (wait.toMillis() + 999) / 1000);
                });
        Instant startOfDay = LocalDate.ofInstant(now, KST).atStartOfDay(KST).toInstant();
        long sentToday = verifications.countByEmailAndPurposeInAndCreatedAtGreaterThanEqual(email,
                purpose.limitGroup(), startOfDay);
        if (sentToday >= DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.EMAIL_DAILY_LIMIT, "오늘은 더 보낼 수 없습니다. 내일 다시 시도해 주세요.");
        }
    }

    /** 틀린 횟수는 에러로 끝나도 남아야 해서 업무 예외로는 롤백하지 않는다 */
    @Transactional(noRollbackFor = BusinessException.class)
    public void confirm(VerificationPurpose purpose, String email, String code) {
        confirm(purpose, email, code, null);
    }

    /** 이메일 변경 — 그 부원이 받은 번호만 인정한다 (같은 메일로 다른 부원이 나중에 받았으면 무효) */
    @Transactional(noRollbackFor = BusinessException.class)
    public void confirm(VerificationPurpose purpose, String email, String code, Long memberId) {
        Instant now = clock.instant();
        EmailVerification verification = verifications.findFirstByEmailAndPurposeOrderByIdDesc(email, purpose)
                .filter(found -> memberId == null || memberId.equals(found.getMemberId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID,
                        "인증번호를 먼저 받아 주세요."));
        if (verification.isExpired(now)) {
            throw expired();
        }
        if (!matches(verification.getCodeHash(), TokenHasher.sha256(code))) {
            int remaining = verification.fail();
            if (remaining == 0) {
                throw expired();
            }
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID,
                    "인증번호가 맞지 않습니다. 남은 횟수 " + remaining + "회");
        }
        verification.markVerified(now);
    }

    private static BusinessException expired() {
        return new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED, "인증번호가 만료됐습니다. 다시 받아 주세요.");
    }

    private static boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII));
    }

}
