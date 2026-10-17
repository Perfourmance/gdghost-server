package com.gdghost.member;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다른 엔티티와의 관계는 id 값으로만 둔다 — 지연 로딩 N+1을 구조적으로 막는다.
 * 탈퇴하면 login_id · email · nickname 등 개인정보 컬럼이 NULL이 된다 (ERD).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loginId;
    private String passwordHash;
    private String email;
    private String name;
    private String nickname;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int generation;

    private Long inviteCodeId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String studentNo;

    private String department;

    @Enumerated(EnumType.STRING)
    private Position position;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    private String bio;
    private String statusNote;
    private Instant statusNoteUpdatedAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String staffTitle;
    private boolean acceptQuestion;
    private boolean acceptCoffee;
    private String coffeeMemo;
    private boolean pushOn;
    private boolean hiddenFromList;
    private Instant passwordChangedAt;
    private Instant nicknameChangedAt;
    private Instant emailChangedAt;
    private Instant lastSeenAt;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;
    private Instant withdrawnAt;
    private Instant suspendedAt;
    private String suspendedReason;
    private Long suspendedBy;

    /** 연락 수단 없음 · 커피챗 꺼짐 · 질문 받기 켜짐으로 시작 (API 설계서 가입 완료 비고) */
    public static Member join(String loginId, String passwordHash, String email, int generation, long inviteCodeId,
            MemberProfile profile, Instant now) {
        Member member = new Member();
        member.loginId = loginId;
        member.passwordHash = passwordHash;
        member.email = email;
        member.generation = generation;
        member.inviteCodeId = inviteCodeId;
        member.name = profile.name();
        member.nickname = profile.nickname();
        member.position = profile.position();
        member.studentNo = profile.studentNo();
        member.department = profile.department();
        member.status = profile.status();
        member.statusNote = profile.statusNote();
        member.statusNoteUpdatedAt = profile.statusNote() != null ? now : null;
        member.bio = profile.bio();
        member.role = Role.MEMBER;
        member.acceptQuestion = true;
        member.acceptCoffee = false;
        member.pushOn = false;
        member.hiddenFromList = false;
        member.updatedAt = now;
        return member;
    }

    public boolean isActive() {
        return withdrawnAt == null && suspendedAt == null;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.updatedAt = now;
    }

    public void changeEmail(String email, Instant now) {
        this.email = email;
        this.emailChangedAt = now;
        this.updatedAt = now;
    }

    public void markSeen(Instant now) {
        this.lastSeenAt = now;
    }

    /** 이미 운영진이면 직함만 바꾼다 */
    public void assignStaff(String staffTitle, Instant now) {
        if (!isActive() || role == Role.DEV) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "탈퇴 · 정지 · 개발자 계정은 운영진으로 지정할 수 없습니다.");
        }
        this.role = Role.STAFF;
        this.staffTitle = staffTitle;
        this.updatedAt = now;
    }

    public void promoteToDev(Instant now) {
        this.role = Role.DEV;
        this.staffTitle = null;
        this.updatedAt = now;
    }

}
