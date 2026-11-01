package com.gdghost.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.gdghost.common.security.Role;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByLoginIdIn(Collection<String> loginIds);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, long id);

    /** 프로필 · 연락 수단 저장 — 태그 · 연락 수단을 지우고 다시 넣으므로 같은 부원의 동시 저장을 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForUpdate(long id);

    /** 활동 중인 운영진. 행을 잠가 동시에 탈퇴 · 해제해도 0명이 되지 않게 한다 */
    @Query(value = "SELECT id FROM member WHERE role = 'STAFF' AND withdrawn_at IS NULL ORDER BY id FOR UPDATE",
            nativeQuery = true)
    List<Long> lockActiveStaffIds();

    /** 졸업 처리 대상 — 활동 중인 재학 · 휴학. 학번 조건이 있으면 학번이 없는 사람은 빠진다 */
    @Query("""
            SELECT m FROM Member m
            WHERE m.withdrawnAt IS NULL AND m.suspendedAt IS NULL
              AND m.status <> com.gdghost.member.MemberStatus.GRADUATED
              AND (:generation IS NULL OR m.generation = :generation)
              AND (:studentNoMax IS NULL OR m.studentNo <= :studentNoMax)
            ORDER BY m.generation, m.studentNo, m.id
            """)
    List<Member> findGraduationCandidates(Integer generation, String studentNoMax);

    /** 운영 부원 목록 — 탈퇴자 제외, 목록 숨김은 포함. 조건이 null이면 거르지 않는다. pattern은 LIKE 패턴 */
    @Query("""
            SELECT m FROM Member m
            WHERE m.withdrawnAt IS NULL
              AND (:pattern IS NULL OR m.name LIKE :pattern ESCAPE '\\'
                   OR m.nickname LIKE :pattern ESCAPE '\\' OR m.loginId LIKE :pattern ESCAPE '\\')
              AND (:generation IS NULL OR m.generation = :generation)
              AND (:status IS NULL OR m.status = :status)
              AND (:role IS NULL OR m.role = :role)
              AND (:suspended IS NULL
                   OR (:suspended = TRUE AND m.suspendedAt IS NOT NULL)
                   OR (:suspended = FALSE AND m.suspendedAt IS NULL))
            ORDER BY m.id DESC
            """)
    Page<Member> search(String pattern, Integer generation, MemberStatus status, Role role, Boolean suspended,
            Pageable pageable);

}
