package com.gdghost.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.page.OpsPage;

/** 운영 신고 목록. 대상 미리보기 · 같은 대상 수는 페이지 단위로 종류별 한 번씩 */
@Repository
public class OpsReportReader {

    static final int PREVIEW_LENGTH = 50;

    private static final String SELECT = """
            SELECT r.id, r.target_type, r.target_id, r.reason, r.state, r.created_at, r.handled_at,
                   r.reporter_id, rm.nickname AS reporter_nickname, r.handled_by, hm.nickname AS handler_nickname
            FROM report r
            JOIN member rm ON rm.id = r.reporter_id
            LEFT JOIN member hm ON hm.id = r.handled_by
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OpsReportReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 미처리(OPEN)가 위로, 그 안에서 최신순 */
    OpsPage<OpsReportResponse> list(ReportState state, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("state", state == null ? null : state.name())
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String where = " WHERE (:state IS NULL OR r.state = :state) ";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM report r" + where, params, Long.class);
        List<Row> rows = jdbc.query(SELECT + where + """
                ORDER BY r.state = 'OPEN' DESC, r.created_at DESC, r.id DESC
                LIMIT :limit OFFSET :offset
                """, params, OpsReportReader::row);
        return new OpsPage<>(withTargets(rows), page, size, total);
    }

    Optional<OpsReportResponse> find(long reportId) {
        List<Row> rows = jdbc.query(SELECT + " WHERE r.id = :id", Map.of("id", reportId), OpsReportReader::row);
        return withTargets(rows).stream().findFirst();
    }

    private List<OpsReportResponse> withTargets(List<Row> rows) {
        Map<Long, Target> questions = questions(ids(rows, ReportTargetType.QUESTION));
        Map<Long, Target> answers = answers(ids(rows, ReportTargetType.ANSWER));
        Map<Long, Target> noticeComments = noticeComments(ids(rows, ReportTargetType.NOTICE_COMMENT));
        Map<ReportTargetType, Map<Long, Integer>> counts = new HashMap<>();
        for (ReportTargetType type : ReportTargetType.values()) {
            counts.put(type, counts(type, ids(rows, type)));
        }
        return rows.stream().map(row -> {
            Target target = switch (row.targetType()) {
                case QUESTION -> questions.get(row.targetId());
                case ANSWER -> answers.get(row.targetId());
                case NOTICE_COMMENT -> noticeComments.get(row.targetId());
                default -> null;
            };
            return new OpsReportResponse(row.id(), row.targetType(), row.targetId(),
                    target == null ? null : target.preview(),
                    target == null ? null : target.link(),
                    target == null ? null : target.deletedBy(),
                    row.reason(), counts.get(row.targetType()).getOrDefault(row.targetId(), 1),
                    row.reporter(), row.state(), row.createdAt(), row.handledBy(), row.handledAt());
        }).toList();
    }

    private Map<Long, Target> questions(List<Long> ids) {
        Map<Long, Target> targets = new HashMap<>();
        if (ids.isEmpty()) {
            return targets;
        }
        jdbc.query("SELECT id, title, deleted_by FROM question WHERE id IN (:ids)", Map.of("ids", ids), rs -> {
            long id = rs.getLong("id");
            targets.put(id, new Target(preview(rs.getString("title")), "/questions/" + id,
                    rs.getString("deleted_by")));
        });
        return targets;
    }

    private Map<Long, Target> answers(List<Long> ids) {
        Map<Long, Target> targets = new HashMap<>();
        if (ids.isEmpty()) {
            return targets;
        }
        jdbc.query("SELECT id, question_id, body, deleted_by FROM answer WHERE id IN (:ids)", Map.of("ids", ids),
                rs -> {
                    long id = rs.getLong("id");
                    targets.put(id, new Target(preview(rs.getString("body")),
                            "/questions/" + rs.getLong("question_id") + "#answer-" + id, rs.getString("deleted_by")));
                });
        return targets;
    }

    private Map<Long, Target> noticeComments(List<Long> ids) {
        Map<Long, Target> targets = new HashMap<>();
        if (ids.isEmpty()) {
            return targets;
        }
        jdbc.query("SELECT id, notice_id, body, deleted_by FROM notice_comment WHERE id IN (:ids)",
                Map.of("ids", ids), rs -> {
                    long id = rs.getLong("id");
                    targets.put(id, new Target(preview(rs.getString("body")),
                            "/notices/" + rs.getLong("notice_id") + "#comment-" + id, rs.getString("deleted_by")));
                });
        return targets;
    }

    /** 이 신고를 포함한 같은 대상의 신고 수 (상태 무관) */
    private Map<Long, Integer> counts(ReportTargetType type, List<Long> ids) {
        Map<Long, Integer> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                SELECT target_id, COUNT(*) AS n FROM report
                WHERE target_type = :type AND target_id IN (:ids)
                GROUP BY target_id
                """, Map.of("type", type.name(), "ids", ids),
                rs -> {
                    counts.put(rs.getLong("target_id"), rs.getInt("n"));
                });
        return counts;
    }

    private static List<Long> ids(List<Row> rows, ReportTargetType type) {
        return rows.stream().filter(row -> row.targetType() == type).map(Row::targetId).distinct().toList();
    }

    static String preview(String text) {
        if (text == null || text.codePointCount(0, text.length()) <= PREVIEW_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        long handlerId = rs.getLong("handled_by");
        OpsReportResponse.Person handledBy = rs.wasNull() ? null
                : new OpsReportResponse.Person(handlerId, rs.getString("handler_nickname"));
        return new Row(rs.getLong("id"), ReportTargetType.valueOf(rs.getString("target_type")),
                rs.getLong("target_id"), rs.getString("reason"), ReportState.valueOf(rs.getString("state")),
                instant(rs, "created_at"), instant(rs, "handled_at"),
                new OpsReportResponse.Person(rs.getLong("reporter_id"), rs.getString("reporter_nickname")),
                handledBy);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record Row(long id, ReportTargetType targetType, long targetId, String reason, ReportState state,
                       Instant createdAt, Instant handledAt, OpsReportResponse.Person reporter,
                       OpsReportResponse.Person handledBy) {
    }

    private record Target(String preview, String link, String deletedBy) {
    }

}
