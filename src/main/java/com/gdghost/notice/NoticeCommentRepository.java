package com.gdghost.notice;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    /** 작성자 삭제와 신고 숨김 · 되돌리기를 한 줄로 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM NoticeComment c WHERE c.id = :id")
    Optional<NoticeComment> findForUpdate(long id);

}
