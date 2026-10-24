package com.gdghost.question;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 익명 질문의 글 단위 번호. 0이 글쓴이 */
@Repository
public class QuestionAnonAliasWriter {

    private final NamedParameterJdbcTemplate jdbc;

    public QuestionAnonAliasWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void assignAuthor(long questionId, long authorId) {
        jdbc.update("""
                INSERT INTO question_anon_alias (question_id, member_id, alias_no) VALUES (:questionId, :memberId, 0)
                """, Map.of("questionId", questionId, "memberId", authorId));
    }

}
