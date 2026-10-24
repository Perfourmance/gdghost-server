package com.gdghost.question;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 질문 상세. 답변 트리는 question_id로 한 번 읽어 AnswerTree가 조립한다 */
@Repository
public class QuestionDetailReader {

    private final NamedParameterJdbcTemplate jdbc;

    public QuestionDetailReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record QuestionRow(long id, QuestionCategory category, String title, String body, boolean anonymous,
            AuthorRef.Row author, QuestionState state, Long adoptedAnswerId, boolean editedAfterAnswer,
            int viewCount, Instant createdAt, Instant updatedAt) {
    }

    record AnswerRow(long id, Long parentId, AuthorRef.Row author, boolean anonymous, Integer aliasNo, String body,
            Instant createdAt, Instant editedAt, boolean deleted, String deletedBy) {
    }

    /** 삭제 · 숨김이면 비어 있다 */
    Optional<QuestionRow> find(long questionId) {
        return jdbc.query("""
                SELECT q.id, q.category, q.title, q.body, q.is_anonymous, q.author_id, q.state, q.adopted_answer_id,
                       q.edited_after_answer, q.view_count, q.created_at, q.updated_at,
                """ + AuthorRows.COLUMNS + """
                FROM question q JOIN member m ON m.id = q.author_id
                WHERE q.id = :id AND q.deleted_at IS NULL
                """, Map.of("id", questionId), (rs, i) -> new QuestionRow(rs.getLong("id"),
                QuestionCategory.valueOf(rs.getString("category")), rs.getString("title"), rs.getString("body"),
                rs.getBoolean("is_anonymous"), AuthorRows.read(rs, "author_id"),
                QuestionState.valueOf(rs.getString("state")), rs.getObject("adopted_answer_id", Long.class),
                rs.getBoolean("edited_after_answer"), rs.getInt("view_count"), instant(rs, "created_at"),
                instant(rs, "updated_at"))).stream().findFirst();
    }

    /** 지운 답변도 포함 (하위 답글이 남으면 자리를 둔다). id 오름차순 = 작성 순 */
    List<AnswerRow> answers(long questionId) {
        return jdbc.query("""
                SELECT a.id, a.parent_id, a.author_id, a.is_anonymous, al.alias_no, a.body, a.created_at, a.edited_at,
                       a.deleted_at IS NOT NULL AS deleted, a.deleted_by,
                """ + AuthorRows.COLUMNS + """
                FROM answer a
                JOIN member m ON m.id = a.author_id
                LEFT JOIN question_anon_alias al ON al.question_id = a.question_id AND al.member_id = a.author_id
                WHERE a.question_id = :id
                ORDER BY a.id
                """, Map.of("id", questionId), (rs, i) -> new AnswerRow(rs.getLong("id"),
                rs.getObject("parent_id", Long.class), AuthorRows.read(rs, "author_id"),
                rs.getBoolean("is_anonymous"), rs.getObject("alias_no", Integer.class), rs.getString("body"),
                instant(rs, "created_at"), instant(rs, "edited_at"), rs.getBoolean("deleted"),
                rs.getString("deleted_by")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

}
