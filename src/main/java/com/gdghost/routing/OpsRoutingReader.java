package com.gdghost.routing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.Role;
import com.gdghost.question.AnonLabels;
import com.gdghost.question.AuthorRef;
import com.gdghost.question.QuestionCategory;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 운영 화면 조회. 익명 질문의 질문자는 어떤 응답에도 넣지 않는다 */
@Repository
class OpsRoutingReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    OpsRoutingReader(NamedParameterJdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** 오래 기다린 순 */
    OpsPage<OpsRoutingResponse.StaleQuestion> staleQuestions(long viewerId, int page, int size) {
        Instant now = clock.instant();
        MapSqlParameterSource params = new MapSqlParameterSource("before", utc(now.minus(RoutingJob.STALE_AFTER)))
                .addValue("limit", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM question
                WHERE state = 'WAITING' AND deleted_at IS NULL AND created_at <= :before
                """, params, Long.class);
        List<OpsRoutingResponse.StaleQuestion> items = jdbc.query("""
                SELECT q.id, q.category, q.title, q.is_anonymous, q.author_id, q.created_at,
                       m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn,
                       COALESCE(SUM(r.sent_at IS NOT NULL), 0) AS sent_count,
                       COALESCE(SUM(r.state = 'PASSED'), 0) AS passed_count
                FROM question q
                JOIN member m ON m.id = q.author_id
                LEFT JOIN routing r ON r.question_id = q.id AND r.stage <> 'STAFF'
                WHERE q.state = 'WAITING' AND q.deleted_at IS NULL AND q.created_at <= :before
                GROUP BY q.id
                ORDER BY q.created_at, q.id
                LIMIT :limit OFFSET :offset
                """, params, (rs, i) -> {
            Instant createdAt = rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
            AuthorRef.Row author = new AuthorRef.Row(rs.getLong("author_id"), rs.getString("nickname"),
                    rs.getInt("generation"), Role.valueOf(rs.getString("role")), rs.getBoolean("withdrawn"));
            return new OpsRoutingResponse.StaleQuestion(rs.getLong("id"),
                    QuestionCategory.valueOf(rs.getString("category")), rs.getString("title"),
                    AuthorRef.of(author, rs.getBoolean("is_anonymous"), AnonLabels.QUESTION_AUTHOR, viewerId),
                    createdAt, Duration.between(createdAt, now).toDays(), rs.getInt("sent_count"),
                    rs.getInt("passed_count"));
        });
        return new OpsPage<>(items, page, size, total == null ? 0 : total);
    }

    /** 최신순. 운영진 단계는 채널 알림이라 사람별 이력에 넣지 않고, 보내지 않은 지목(CANDIDATE)도 뺀다 */
    OpsPage<OpsRoutingResponse.Log> logs(OpsRoutingResponse.LogState state, Long memberId, int page, int size) {
        String where = """
                 WHERE r.stage <> 'STAFF' AND r.state <> 'CANDIDATE'
                """ + (state == null ? "" : " AND r.state = :state ")
                + (memberId == null ? "" : " AND r.member_id = :member ");
        MapSqlParameterSource params = new MapSqlParameterSource("limit", size)
                .addValue("offset", (long) page * size)
                .addValue("state", state == null ? null : state.name())
                .addValue("member", memberId);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM routing r" + where, params, Long.class);
        List<OpsRoutingResponse.Log> items = jdbc.query("""
                SELECT r.id, r.question_id, q.title, r.member_id, m.nickname, r.stage, r.rank_no, r.state,
                       r.sent_at, r.responded_at, r.score_detail
                FROM routing r
                JOIN question q ON q.id = r.question_id
                JOIN member m ON m.id = r.member_id
                """ + where + " ORDER BY r.id DESC LIMIT :limit OFFSET :offset", params, (rs, i) -> {
            String detail = rs.getString("score_detail");
            return new OpsRoutingResponse.Log(rs.getLong("id"), rs.getLong("question_id"), rs.getString("title"),
                    new OpsRoutingResponse.LogMember(rs.getLong("member_id"), rs.getString("nickname")),
                    rs.getString("stage"), rs.getObject("rank_no", Integer.class), rs.getString("state"),
                    instant(rs.getObject("sent_at", LocalDateTime.class)),
                    instant(rs.getObject("responded_at", LocalDateTime.class)),
                    detail == null ? null : json.readValue(detail, DETAIL));
        });
        return new OpsPage<>(items, page, size, total == null ? 0 : total);
    }

    /** 기간은 한국 날짜, 보낸 시각 기준 */
    OpsRoutingResponse.Stats stats(LocalDate from, LocalDate to) {
        MapSqlParameterSource period = new MapSqlParameterSource("start", startOf(from))
                .addValue("end", startOf(to.plusDays(1)));
        OpsRoutingResponse.Stats totals = jdbc.queryForObject("""
                SELECT COALESCE(SUM(stage <> 'STAFF'), 0) AS sent,
                       COALESCE(SUM(stage <> 'STAFF' AND state = 'ANSWERED'), 0) AS answered,
                       COALESCE(SUM(stage <> 'STAFF' AND state = 'PASSED'), 0) AS passed,
                       COALESCE(SUM(stage = 'STAFF'), 0) AS staff_first
                FROM routing WHERE sent_at >= :start AND sent_at < :end
                """, period, (rs, i) -> {
            long sent = rs.getLong("sent");
            long answered = rs.getLong("answered");
            long passed = rs.getLong("passed");
            return new OpsRoutingResponse.Stats(sent, answered, passed, rs.getLong("staff_first"),
                    rate(answered, sent), rate(passed, sent), 0, List.of());
        });
        List<OpsRoutingResponse.VersionStats> byVersion = jdbc.query("""
                SELECT JSON_VALUE(score_detail, '$.version') AS version, COUNT(*) AS sent,
                       SUM(state = 'ANSWERED') AS answered, SUM(state = 'PASSED') AS passed
                FROM routing
                WHERE sent_at >= :start AND sent_at < :end AND stage <> 'STAFF' AND score_detail IS NOT NULL
                GROUP BY version ORDER BY version
                """, period, (rs, i) -> {
            long sent = rs.getLong("sent");
            return new OpsRoutingResponse.VersionStats(rs.getString("version"), sent,
                    rate(rs.getLong("answered"), sent), rate(rs.getLong("passed"), sent));
        });
        double fallback = jdbc.queryForObject("""
                SELECT COUNT(*) AS total, COALESCE(SUM(state = 'FALLBACK'), 0) AS fallbacks
                FROM ai_log WHERE created_at >= :start AND created_at < :end
                """, period, (rs, i) -> rate(rs.getLong("fallbacks"), rs.getLong("total")));
        return new OpsRoutingResponse.Stats(totals.sentCount(), totals.answeredCount(), totals.passedCount(),
                totals.staffFirstCount(), totals.conversionRate(), totals.passRate(), fallback, byVersion);
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0 : Math.round((double) part / whole * 10_000) / 10_000.0;
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay(KST).toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private static LocalDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

}
