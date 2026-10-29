package com.gdghost.question;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    /** 지운 것까지 포함한 마지막 제출 시각 */
    @Query("SELECT MAX(a.createdAt) FROM Answer a WHERE a.authorId = :authorId")
    Instant lastCreatedAt(long authorId);

}
