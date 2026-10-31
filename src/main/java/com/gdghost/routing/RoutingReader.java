package com.gdghost.routing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.embedding.EmbeddingStore;
import com.gdghost.member.MemberStatus;
import com.gdghost.member.Position;

/** 후보 계산 재료와 전달 현황 읽기. 후보마다 조회하지 않고 IN 한 번으로 모은다 */
@Repository
public class RoutingReader {

    static final int DAILY_LIMIT = 3;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    record PoolMember(long id, String department, Position position, int generation, MemberStatus status,
            Instant lastSeenAt) {
    }

    record Delivery(long memberId, String stage, String state, String nickname, String scoreDetail, Instant sentAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final EmbeddingStore embeddings;

    public RoutingReader(NamedParameterJdbcTemplate jdbc, EmbeddingStore embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
    }

    /** 질문 받기 꺼짐 · 목록 숨김 · 탈퇴 · 정지 · 글쓴이 제외 */
    List<PoolMember> pool(long authorId) {
        return jdbc.query("""
                SELECT id, department, position, generation, status, last_seen_at FROM member
                WHERE accept_question = TRUE AND hidden_from_list = FALSE
                  AND withdrawn_at IS NULL AND suspended_at IS NULL AND id <> :author
                """, Map.of("author", authorId), (rs, i) -> new PoolMember(rs.getLong("id"),
                rs.getString("department"), Position.valueOf(rs.getString("position")),
                rs.getInt("generation"), MemberStatus.valueOf(rs.getString("status")),
                instant(rs, "last_seen_at")));
    }

    /** 이 질문에서 이미 보냈거나 넘긴 사람, 지목한 사람 — 다시 후보로 만들지 않는다 */
    Set<Long> alreadyRouted(long questionId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT member_id FROM routing
                WHERE question_id = :question AND state <> 'CANCELED'
                  AND NOT (stage = 'MEMBER' AND state = 'CANDIDATE')
                """, Map.of("question", questionId), Long.class));
    }

    /** 직전에 알림이 나간 질문에서 실제로 받은 사람. 보내지 않은 후보는 지목이 아니다 */
    Set<Long> previousQuestionRouted(long questionId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT member_id FROM routing
                WHERE sent_at IS NOT NULL AND stage <> 'STAFF' AND question_id = (
                    SELECT MAX(question_id) FROM routing
                    WHERE question_id < :question AND sent_at IS NOT NULL AND stage <> 'STAFF')
                """, Map.of("question", questionId), Long.class));
    }

    /** 오늘(KST) 받은 건수 */
    Map<Long, Integer> receivedToday(List<Long> memberIds, Instant now) {
        Map<Long, Integer> counts = new HashMap<>();
        if (memberIds.isEmpty()) {
            return counts;
        }
        LocalDateTime start = now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant()
                .atOffset(ZoneOffset.UTC).toLocalDateTime();
        jdbc.query("""
                SELECT member_id, COUNT(*) AS received FROM routing
                WHERE member_id IN (:ids) AND sent_at >= :start
                GROUP BY member_id
                """, new MapSqlParameterSource("ids", memberIds).addValue("start", start),
                rs -> {
                    counts.put(rs.getLong("member_id"), rs.getInt("received"));
                });
        return counts;
    }

    Map<Long, List<String>> experiences(List<Long> memberIds) {
        Map<Long, List<String>> names = new HashMap<>();
        if (memberIds.isEmpty()) {
            return names;
        }
        jdbc.query("""
                SELECT mt.member_id, t.name FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                WHERE mt.member_id IN (:ids) AND t.kind = 'EXPERIENCE'
                """, Map.of("ids", memberIds), rs -> {
            names.computeIfAbsent(rs.getLong("member_id"), id -> new ArrayList<>()).add(rs.getString("name"));
        });
        return names;
    }

    /** 익명 답변 · 답글 · 지운 글은 뺀다 — 익명 답변자가 추측되지 않게 */
    Map<Long, List<CandidateScorer.PastAnswer>> pastAnswers(List<Long> memberIds, long questionId, String model) {
        Map<Long, List<CandidateScorer.PastAnswer>> answers = new HashMap<>();
        if (memberIds.isEmpty()) {
            return answers;
        }
        jdbc.query("""
                SELECT a.author_id, qe.vector, (q.adopted_answer_id = a.id) AS adopted
                FROM answer a
                JOIN question q ON q.id = a.question_id
                JOIN question_embedding qe ON qe.question_id = q.id AND qe.model = :model
                WHERE a.author_id IN (:ids) AND q.id <> :question
                  AND a.depth = 0 AND a.is_anonymous = FALSE
                  AND a.deleted_at IS NULL AND q.deleted_at IS NULL
                """, new MapSqlParameterSource("ids", memberIds).addValue("question", questionId)
                .addValue("model", model), rs -> {
            answers.computeIfAbsent(rs.getLong("author_id"), id -> new ArrayList<>())
                    .add(new CandidateScorer.PastAnswer(embeddings.vector(rs.getString("vector")),
                            rs.getBoolean("adopted")));
        });
        return answers;
    }

    /** 프로필 답변 수와 같은 기준 — 익명 · 답글 · 지운 글 제외 */
    Map<Long, Integer> answerCounts(List<Long> memberIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (memberIds.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                SELECT a.author_id, COUNT(*) AS answers FROM answer a
                JOIN question q ON q.id = a.question_id
                WHERE a.author_id IN (:ids) AND a.depth = 0 AND a.is_anonymous = FALSE
                  AND a.deleted_at IS NULL AND q.deleted_at IS NULL
                GROUP BY a.author_id
                """, Map.of("ids", memberIds), rs -> {
            counts.put(rs.getLong("author_id"), rs.getInt("answers"));
        });
        return counts;
    }

    Map<Long, Integer> passCounts(List<Long> memberIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (memberIds.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                SELECT member_id, COUNT(*) AS passes FROM routing
                WHERE member_id IN (:ids) AND state = 'PASSED'
                GROUP BY member_id
                """, Map.of("ids", memberIds), rs -> {
            counts.put(rs.getLong("member_id"), rs.getInt("passes"));
        });
        return counts;
    }

    /** 취소된 것은 뺀다. 보낸 것 먼저, 그 안에서 순위 · 만든 순 */
    List<Delivery> deliveries(long questionId) {
        return jdbc.query("""
                SELECT r.member_id, r.stage, r.state, m.nickname, r.score_detail, r.sent_at
                FROM routing r JOIN member m ON m.id = r.member_id
                WHERE r.question_id = :question AND r.state <> 'CANCELED'
                ORDER BY r.sent_at IS NULL, r.sent_at, r.rank_no, r.id
                """, Map.of("question", questionId), (rs, i) -> new Delivery(rs.getLong("member_id"),
                rs.getString("stage"), rs.getString("state"), rs.getString("nickname"),
                rs.getString("score_detail"), instant(rs, "sent_at")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

}
