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

    /** 이미 있으면 그 번호, 없으면 다음 번호. 질문 행을 잠근 트랜잭션 안에서 부른다 */
    int assign(long questionId, long memberId) {
        Map<String, Long> params = Map.of("questionId", questionId, "memberId", memberId);
        Integer existing = jdbc.query("""
                SELECT alias_no FROM question_anon_alias WHERE question_id = :questionId AND member_id = :memberId
                """, params, (rs, i) -> rs.getInt("alias_no")).stream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        jdbc.update("""
                INSERT INTO question_anon_alias (question_id, member_id, alias_no)
                SELECT :questionId, :memberId, COALESCE(MAX(alias_no), 0) + 1
                FROM question_anon_alias WHERE question_id = :questionId
                """, params);
        return jdbc.queryForObject("""
                SELECT alias_no FROM question_anon_alias WHERE question_id = :questionId AND member_id = :memberId
                """, params, Integer.class);
    }

}
