package com.gdghost.question;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Modifying
    @Query("UPDATE Question q SET q.viewCount = q.viewCount + 1 WHERE q.id = :id")
    void incrementViewCount(long id);

    /** 답변 작성 시 익명 번호 · 상태 전환을 한 줄로 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Question q WHERE q.id = :id")
    Optional<Question> findForUpdate(long id);

}
