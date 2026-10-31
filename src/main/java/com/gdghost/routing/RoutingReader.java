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
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
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

    /** @param replacements 넘기기로 보충한 수. 보충은 rank_no가 없다 */
    record Waiting(long id, int roundMembers, int passed, int replacements, int total) {

        boolean isFirstRound() {
            return roundMembers == 0;
        }

        /** 처음이면 정해진 인원, 그 뒤로는 넘긴 만큼만 — 무응답에는 더 보내지 않는다 */
        int slots(MatchingConfig config) {
            int wanted = isFirstRound() ? config.maxCandidates() : passed - replacements;
            return Math.max(0, Math.min(wanted, config.maxPerQuestion() - total));
        }
    }

    record SeniorCard(long id, String nickname, int generation, MemberStatus status, Position position,
            String statusNote, boolean acceptCoffee) {
    }

    record Scheduled(long id, long questionId, long memberId) {
    }

    record Stale(long id, String title, Instant createdAt) {
    }

    /** 지목은 아직 안 보냈거나, 보낸 지 얼마 안 됐으면 그 선배를 기다린다 */
    private static final String NO_ACTIVE_DIRECT = """
              AND NOT EXISTS (SELECT 1 FROM routing d WHERE d.question_id = q.id AND d.stage = 'DIRECT'
                              AND (d.state IN ('CANDIDATE', 'ANSWERED')
                                   OR (d.state = 'SENT' AND d.sent_at > :directBefore)))
            """;

    private static final String WAITING_COUNTS = """
            SELECT q.id,
                   COALESCE(SUM(r.stage = 'MEMBER'), 0) AS round_members,
                   COALESCE(SUM(r.stage = 'MEMBER' AND r.state = 'PASSED'), 0) AS passed,
                   COALESCE(SUM(r.stage = 'MEMBER' AND r.rank_no IS NULL), 0) AS replacements,
                   COUNT(r.id) AS total
            FROM question q
            LEFT JOIN routing r ON r.question_id = q.id AND r.stage <> 'STAFF' AND r.state <> 'CANCELED'
            WHERE q.state = 'WAITING' AND q.deleted_at IS NULL
              AND q.created_at <= :before AND q.created_at > :after %s
            """ + NO_ACTIVE_DIRECT + """
            GROUP BY q.id ORDER BY q.id
            """;

    private static final RowMapper<Waiting> WAITING_ROW = (rs, i) -> new Waiting(rs.getLong("id"),
            rs.getInt("round_members"), rs.getInt("passed"), rs.getInt("replacements"), rs.getInt("total"));

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

    /** 이 질문에서 이미 받았거나 넘긴 사람, 지목한 사람 — 같은 사람에게 두 번 가지 않게 */
    Set<Long> alreadyRouted(long questionId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT member_id FROM routing
                WHERE question_id = :question AND state <> 'CANCELED' AND stage <> 'STAFF'
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

    /** 오늘(KST) 받은 건수. 운영진 단계는 채널 알림이라 세지 않는다 */
    Map<Long, Integer> receivedToday(List<Long> memberIds, Instant now) {
        Map<Long, Integer> counts = new HashMap<>();
        if (memberIds.isEmpty()) {
            return counts;
        }
        LocalDateTime start = now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant()
                .atOffset(ZoneOffset.UTC).toLocalDateTime();
        jdbc.query("""
                SELECT member_id, COUNT(*) AS received FROM routing
                WHERE member_id IN (:ids) AND sent_at >= :start AND stage <> 'STAFF'
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

    /** 답을 기다리고 지목이 진행 중이 아닌 질문. 3일이 지나면 운영진 목록으로 넘어간다 */
    List<Waiting> waitingForMembers(Instant createdBefore, Instant createdAfter, Instant directBefore) {
        return jdbc.query(WAITING_COUNTS.formatted(""), window(createdBefore, createdAfter, directBefore), WAITING_ROW);
    }

    Optional<Waiting> waitingForMembers(long questionId, Instant createdBefore, Instant createdAfter,
            Instant directBefore) {
        return jdbc.query(WAITING_COUNTS.formatted("AND q.id = :question"),
                window(createdBefore, createdAfter, directBefore).addValue("question", questionId), WAITING_ROW)
                .stream().findFirst();
    }

    /** 위 조건 + 운영진에게 아직 알리지 않은 것 */
    List<Long> waitingForStaff(Instant createdBefore, Instant createdAfter, Instant directBefore) {
        return jdbc.queryForList("""
                SELECT q.id FROM question q
                WHERE q.state = 'WAITING' AND q.deleted_at IS NULL
                  AND q.created_at <= :before AND q.created_at > :after
                  AND NOT EXISTS (SELECT 1 FROM routing r WHERE r.question_id = q.id AND r.stage = 'STAFF')
                """ + NO_ACTIVE_DIRECT + " ORDER BY q.id", window(createdBefore, createdAfter, directBefore), Long.class);
    }

    /** 22–08시에 지목돼 보내지 않은 것 */
    List<Scheduled> scheduledDirects() {
        return jdbc.query("""
                SELECT id, question_id, member_id FROM routing
                WHERE stage = 'DIRECT' AND state = 'CANDIDATE' ORDER BY id
                """, (rs, i) -> new Scheduled(rs.getLong("id"), rs.getLong("question_id"), rs.getLong("member_id")));
    }

    /** 대표 한 명. 운영진 단계는 채널 알림이라 행을 질문당 하나만 남긴다 */
    Optional<Long> anyActiveStaff() {
        return jdbc.queryForList("""
                SELECT id FROM member
                WHERE role = 'STAFF' AND withdrawn_at IS NULL AND suspended_at IS NULL
                ORDER BY id LIMIT 1
                """, Map.of(), Long.class).stream().findFirst();
    }

    /** 내가 지목받아 아직 반응하지 않은 행 */
    Optional<Long> mySentRouting(long questionId, long memberId) {
        return jdbc.queryForList("""
                SELECT id FROM routing
                WHERE question_id = :question AND member_id = :member AND state = 'SENT' AND stage <> 'STAFF'
                """, Map.of("question", questionId, "member", memberId), Long.class).stream().findFirst();
    }

    List<Stale> staleQuestions(Instant createdBefore) {
        return jdbc.query("""
                SELECT id, title, created_at FROM question
                WHERE state = 'WAITING' AND deleted_at IS NULL AND created_at <= :before
                ORDER BY id
                """, Map.of("before", utc(createdBefore)), (rs, i) -> new Stale(rs.getLong("id"), rs.getString("title"),
                instant(rs, "created_at")));
    }

    private static MapSqlParameterSource window(Instant createdBefore, Instant createdAfter, Instant directBefore) {
        return new MapSqlParameterSource("before", utc(createdBefore)).addValue("after", utc(createdAfter))
                .addValue("directBefore", utc(directBefore));
    }

    /** INTEREST 태그가 아니면 비어 있다 */
    Optional<String> interestName(long tagId) {
        return jdbc.queryForList("SELECT name FROM tag WHERE id = :id AND kind = 'INTEREST'", Map.of("id", tagId),
                String.class).stream().findFirst();
    }

    /** 그 분야를 고른 부원의 사유 문구. "백엔드 · Spring"처럼 세부 기술이 있으면 첫 번째를 붙인다 */
    Map<Long, String> matchedInterests(List<Long> memberIds, long interestTagId, String interestName) {
        Map<Long, String> labels = new HashMap<>();
        if (memberIds.isEmpty()) {
            return labels;
        }
        jdbc.query("""
                SELECT mt.member_id, t.kind, t.name FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                WHERE mt.member_id IN (:ids) AND (t.id = :tag OR t.parent_id = :tag)
                ORDER BY t.kind = 'SKILL', t.sort_order, t.id
                """, new MapSqlParameterSource("ids", memberIds).addValue("tag", interestTagId), rs -> {
            long member = rs.getLong("member_id");
            if ("INTEREST".equals(rs.getString("kind"))) {
                labels.put(member, interestName);
            } else if (interestName.equals(labels.get(member))) {
                labels.put(member, interestName + " · " + rs.getString("name"));
            }
        });
        return labels;
    }

    Map<Long, SeniorCard> seniorCards(List<Long> memberIds) {
        Map<Long, SeniorCard> cards = new HashMap<>();
        if (memberIds.isEmpty()) {
            return cards;
        }
        jdbc.query("""
                SELECT id, nickname, generation, status, position, status_note, accept_coffee
                FROM member WHERE id IN (:ids)
                """, Map.of("ids", memberIds), rs -> {
            cards.put(rs.getLong("id"), new SeniorCard(rs.getLong("id"), rs.getString("nickname"),
                    rs.getInt("generation"), MemberStatus.valueOf(rs.getString("status")),
                    Position.valueOf(rs.getString("position")), rs.getString("status_note"),
                    rs.getBoolean("accept_coffee")));
        });
        return cards;
    }

    private static LocalDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
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
