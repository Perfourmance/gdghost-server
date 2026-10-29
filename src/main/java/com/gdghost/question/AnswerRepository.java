package com.gdghost.question;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    /** 지운 것까지 포함한 마지막 제출 시각 */
    @Query("SELECT MAX(a.createdAt) FROM Answer a WHERE a.authorId = :authorId")
    Instant lastCreatedAt(long authorId);

    /** 엔티티를 올리지 않고 질문 id만 — 질문 행을 먼저 잠근 뒤 답변을 새로 읽기 위해 */
    @Query("SELECT a.questionId FROM Answer a WHERE a.id = :id")
    Optional<Long> findQuestionId(long id);

}
