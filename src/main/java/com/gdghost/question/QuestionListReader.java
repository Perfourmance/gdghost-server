package com.gdghost.question;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.member.MemberActivityReader;

/**
 * 질문 목록. 삭제 · 숨김(deleted_at)은 빠진다.
 * 답변 수 · 답변자 미리보기는 페이지의 id로 한 번씩 읽는다 (N+1 방지).
 */
@Repository
public class QuestionListReader {

    static final int PREVIEW_SIZE = 3;

    private final NamedParameterJdbcTemplate jdbc;

    public QuestionListReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long id, QuestionCategory category, String title, boolean anonymous, AuthorRef.Row author,
            QuestionState state, int viewCount, Instant createdAt) {
    }

    record Answerer(long questionId, AuthorRef.Row author, boolean anonymous, Integer aliasNo) {
    }

    /** 최신순(id 내림차순). authorId가 있으면 그 부원의 질문만(익명 포함) */
    List<Row> list(QuestionCategory category, Long authorId, boolean unanswered, Long afterId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", category != null ? category.name() : null)
                .addValue("authorId", authorId)
                .addValue("unanswered", unanswered)
                .addValue("afterId", afterId)
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT q.id, q.category, q.title, q.is_anonymous, q.author_id, q.state, q.view_count, q.created_at,
                """ + AuthorRows.COLUMNS + """
                FROM question q JOIN member m ON m.id = q.author_id
                WHERE q.deleted_at IS NULL
                  AND (:category IS NULL OR q.category = :category)
                  AND (:authorId IS NULL OR q.author_id = :authorId)
                  AND (:unanswered = FALSE OR q.state = 'WAITING')
                  AND (:afterId IS NULL OR q.id < :afterId)
                ORDER BY q.id DESC
                LIMIT :limit
                """, params, (rs, i) -> new Row(rs.getLong("id"), QuestionCategory.valueOf(rs.getString("category")),
                rs.getString("title"), rs.getBoolean("is_anonymous"), AuthorRows.read(rs, "author_id"),
                QuestionState.valueOf(rs.getString("state")), rs.getInt("view_count"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
    }

    /** 답글 · 삭제 답변은 세지 않는다 (프로필 답변 수와 같은 규칙) */
    Map<Long, Long> answerCounts(List<Long> ids) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query("""
                SELECT a.question_id, COUNT(*) AS cnt
                FROM answer a JOIN question q ON q.id = a.question_id
                WHERE a.question_id IN (:ids) AND
                """ + MemberActivityReader.VISIBLE_ANSWER + """
                GROUP BY a.question_id
                """, Map.of("ids", ids), rs -> {
            result.put(rs.getLong("question_id"), rs.getLong("cnt"));
        });
        return result;
    }

    /** 질문마다 먼저 답한 순으로 서로 다른 답변자 PREVIEW_SIZE명. 같은 사람의 실명 · 익명 답변은 따로 센다 */
    Map<Long, List<Answerer>> answerers(List<Long> ids) {
        Map<Long, List<Answerer>> result = new HashMap<>();
        jdbc.query("""
                SELECT t.question_id, t.author_id, t.is_anonymous, t.alias_no,
                """ + AuthorRows.COLUMNS + """
                FROM (
                    SELECT f.question_id, f.author_id, f.is_anonymous, al.alias_no,
                           ROW_NUMBER() OVER (PARTITION BY f.question_id ORDER BY f.first_id) AS rn
                    FROM (SELECT question_id, author_id, is_anonymous, MIN(id) AS first_id
                          FROM answer
                          WHERE question_id IN (:ids) AND parent_id IS NULL AND deleted_at IS NULL
                          GROUP BY question_id, author_id, is_anonymous) f
                    LEFT JOIN question_anon_alias al
                           ON al.question_id = f.question_id AND al.member_id = f.author_id
                ) t JOIN member m ON m.id = t.author_id
                WHERE t.rn <= :previewSize
                ORDER BY t.question_id, t.rn
                """, new MapSqlParameterSource("ids", ids).addValue("previewSize", PREVIEW_SIZE), rs -> {
            long questionId = rs.getLong("question_id");
            result.computeIfAbsent(questionId, k -> new ArrayList<>()).add(new Answerer(questionId,
                    AuthorRows.read(rs, "author_id"), rs.getBoolean("is_anonymous"),
                    rs.getObject("alias_no", Integer.class)));
        });
        return result;
    }

}
