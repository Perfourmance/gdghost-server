package com.gdghost.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.gdghost.common.security.Role;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findByLoginIdIn(Collection<String> loginIds);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, long id);

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
