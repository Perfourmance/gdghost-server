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

    /** 글이 바뀌면 옛 벡터를 쓰지 않게 지운다. 다시 채우는 것은 embedding 쪽 */
    @Modifying
    @Query(value = "DELETE FROM question_embedding WHERE question_id = :id", nativeQuery = true)
    void deleteEmbedding(long id);

    /** 답변 작성 시 익명 번호 · 상태 전환을 한 줄로 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Question q WHERE q.id = :id")
    Optional<Question> findForUpdate(long id);

}
