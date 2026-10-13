package com.gdghost.auth;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    /** 가입 때는 NULL */
    private Long memberId;

    private String codeHash;

    @Enumerated(EnumType.STRING)
    private VerificationPurpose purpose;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int attemptCount;

    private Instant expiresAt;
    private Instant verifiedAt;
    private Instant createdAt;

    private EmailVerification(String email, Long memberId, VerificationPurpose purpose, String codeHash,
            Instant expiresAt, Instant now) {
        this.email = email;
        this.memberId = memberId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public static EmailVerification create(String email, Long memberId, VerificationPurpose purpose,
            String codeHash, Instant expiresAt, Instant now) {
        return new EmailVerification(email, memberId, purpose, codeHash, expiresAt, now);
    }

    /** 10분이 지났거나 5회 틀렸으면 더 쓸 수 없다 */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now) || attemptCount >= MAX_ATTEMPTS;
    }

    /** @return 남은 시도 횟수 */
    public int fail() {
        attemptCount++;
        return MAX_ATTEMPTS - attemptCount;
    }

    /** 이미 확인됐으면 처음 시각을 둔다 */
    public void markVerified(Instant now) {
        if (verifiedAt == null) {
            verifiedAt = now;
        }
    }

}
