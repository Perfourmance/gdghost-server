package com.gdghost.member;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

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

    public static final Duration NICKNAME_CHANGE_INTERVAL = Duration.ofDays(30);
    public static final Duration EMAIL_CHANGE_INTERVAL = Duration.ofDays(30);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

    /** 지금 바꿀 수 있으면 null */
    public Instant nicknameChangeAvailableAt(Instant now) {
        return availableAt(nicknameChangedAt, NICKNAME_CHANGE_INTERVAL, now);
    }

    /** 지금 바꿀 수 있으면 null */
    public Instant emailChangeAvailableAt(Instant now) {
        return availableAt(emailChangedAt, EMAIL_CHANGE_INTERVAL, now);
    }

    private static Instant availableAt(Instant changedAt, Duration interval, Instant now) {
        if (changedAt == null || !now.isBefore(changedAt.plus(interval))) {
            return null;
        }
        return changedAt.plus(interval);
    }

    public void checkNicknameChangeable(Instant now) {
        Instant availableAt = nicknameChangeAvailableAt(now);
        if (availableAt != null) {
            throw new BusinessException(ErrorCode.NICKNAME_CHANGE_TOO_SOON, "닉네임은 30일에 한 번 바꿀 수 있습니다. "
                    + LocalDate.ofInstant(availableAt, KST) + "부터 다시 바꿀 수 있습니다.");
        }
    }

    /** 중복 · 예약어 검사는 호출하는 쪽에서 */
    public void changeNickname(String nickname, Instant now) {
        checkNicknameChangeable(now);
        this.nickname = nickname;
        this.nicknameChangedAt = now;
        this.updatedAt = now;
    }

    /** 근황이 바뀔 때만 근황 시각을 새로 찍는다 */
    public void changeProfile(String department, Position position, MemberStatus status, String statusNote,
            String bio, Instant now) {
        if (!Objects.equals(this.statusNote, statusNote)) {
            this.statusNoteUpdatedAt = statusNote != null ? now : null;
        }
        this.department = department;
        this.position = position;
        this.status = status;
        this.statusNote = statusNote;
        this.bio = bio;
        this.updatedAt = now;
    }

    public void changeSettings(boolean acceptQuestion, boolean acceptCoffee, String coffeeMemo,
            boolean hiddenFromList, boolean pushOn, Instant now) {
        this.acceptQuestion = acceptQuestion;
        this.acceptCoffee = acceptCoffee;
        this.coffeeMemo = coffeeMemo;
        this.hiddenFromList = hiddenFromList;
        this.pushOn = pushOn;
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

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    public void suspend(String reason, long staffId, Instant now) {
        this.suspendedAt = now;
        this.suspendedReason = reason;
        this.suspendedBy = staffId;
        this.updatedAt = now;
    }

    public void unsuspend(Instant now) {
        this.suspendedAt = null;
        this.suspendedReason = null;
        this.suspendedBy = null;
        this.updatedAt = now;
    }

    public void promoteToDev(Instant now) {
        this.role = Role.DEV;
        this.staffTitle = null;
        this.updatedAt = now;
    }

}
