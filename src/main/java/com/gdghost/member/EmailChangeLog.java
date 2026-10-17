package com.gdghost.member;

import java.time.Instant;

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
public class EmailChangeLog {

    public enum ChangedBy {
        SELF, STAFF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;
    private String oldEmail;
    private String newEmail;

    @Enumerated(EnumType.STRING)
    private ChangedBy changedByType;

    private Long changedBy;

    /** 새 메일 인증 = 반영 시각 */
    private Instant verifiedAt;

    private Instant createdAt;

    /** 본인 변경은 새 메일 인증과 동시에 반영된다 */
    public static EmailChangeLog self(long memberId, String oldEmail, String newEmail, Instant now) {
        EmailChangeLog log = new EmailChangeLog();
        log.memberId = memberId;
        log.oldEmail = oldEmail;
        log.newEmail = newEmail;
        log.changedByType = ChangedBy.SELF;
        log.changedBy = memberId;
        log.verifiedAt = now;
        log.createdAt = now;
        return log;
    }

    /** 운영진 요청은 요청 때 남기고, 부원이 링크를 열면 {@link #verify} */
    public static EmailChangeLog staff(long memberId, String oldEmail, String newEmail, long staffId, Instant now) {
        EmailChangeLog log = new EmailChangeLog();
        log.memberId = memberId;
        log.oldEmail = oldEmail;
        log.newEmail = newEmail;
        log.changedByType = ChangedBy.STAFF;
        log.changedBy = staffId;
        log.createdAt = now;
        return log;
    }

    public void verify(Instant now) {
        this.verifiedAt = now;
    }

}
