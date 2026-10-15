package com.gdghost.invite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    boolean existsByCode(String code);

    Optional<InviteCode> findByCode(String code);

    /** 가입 완료 — 동시에 가입해도 사용 횟수를 넘지 않게 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM InviteCode c WHERE c.id = :id")
    Optional<InviteCode> findByIdForUpdate(long id);

    @Query("""
            SELECT c FROM InviteCode c
            WHERE c.generation = :generation AND c.revokedAt IS NULL AND c.expiresAt > :now
            """)
    List<InviteCode> findUsable(int generation, Instant now);

    @Query(value = """
            SELECT new com.gdghost.invite.InviteCodeCreator(c, m.nickname)
            FROM InviteCode c LEFT JOIN Member m ON m.id = c.createdBy
            ORDER BY c.id DESC
            """, countQuery = "SELECT COUNT(c) FROM InviteCode c")
    Page<InviteCodeCreator> findPageWithCreator(Pageable pageable);

    @Query("""
            SELECT new com.gdghost.invite.InviteCodeMemberResponse(m.id, m.name, m.nickname, m.studentNo, m.createdAt)
            FROM Member m
            WHERE m.inviteCodeId = :codeId AND m.withdrawnAt IS NULL
            ORDER BY m.id
            """)
    List<InviteCodeMemberResponse> findMembers(long codeId);

}
