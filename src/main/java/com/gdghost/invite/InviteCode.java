package com.gdghost.invite;

import java.time.Duration;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

    static final int BOOTSTRAP_MAX_USES = 10;
    static final Duration BOOTSTRAP_LIFETIME = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int generation;

    /** 준비용 코드는 NULL */
    private Long createdBy;

    private int maxUses;
    private int usedCount;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;

    private InviteCode(String code, int generation, Long createdBy, int maxUses, Instant expiresAt, Instant now) {
        this.code = code;
        this.generation = generation;
        this.createdBy = createdBy;
        this.maxUses = maxUses;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** 서비스 첫 시작용 — 개발자 · 첫 운영진이 가입할 코드 */
    public static InviteCode bootstrap(String code, int generation, Instant now) {
        return new InviteCode(code, generation, null, BOOTSTRAP_MAX_USES, now.plus(BOOTSTRAP_LIFETIME), now);
    }

}
