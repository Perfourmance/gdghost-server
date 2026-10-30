package com.gdghost.notice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.page.OpsPage;

/** 운영 공지 목록. 집계 · 댓글 수는 페이지 단위로 한 번씩 */
@Repository
public class OpsNoticeReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final NoticeStatsReader statsReader;

    public OpsNoticeReader(NamedParameterJdbcTemplate jdbc, NoticeStatsReader statsReader) {
        this.jdbc = jdbc;
        this.statsReader = statsReader;
    }

    /** 최신순, 지운 공지 제외 */
    OpsPage<OpsNoticeResponse> list(int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM notice WHERE deleted_at IS NULL", params, Long.class);
        List<Row> rows = jdbc.query("""
                SELECT n.id, n.title, n.is_pinned, n.is_must_read, n.due_date, n.reminded_at, n.created_at,
                       n.author_id, IF(m.withdrawn_at IS NULL, m.nickname, NULL) AS nickname
                FROM notice n
                JOIN member m ON m.id = n.author_id
                WHERE n.deleted_at IS NULL
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT :limit OFFSET :offset
                """, params, OpsNoticeReader::row);
        List<Long> ids = rows.stream().map(Row::id).toList();
        Map<Long, NoticeStats> stats = statsReader.stats(ids);
        Map<Long, Long> commentCounts = commentCounts(ids);
        List<OpsNoticeResponse> items = rows.stream()
                .map(row -> new OpsNoticeResponse(row.id(), row.title(), row.pinned(), row.mustRead(),
                        row.dueDate(), row.author(), stats.get(row.id()), commentCounts.getOrDefault(row.id(), 0L),
                        row.remindedAt(), row.createdAt()))
                .toList();
        return new OpsPage<>(items, page, size, total);
    }

    /** 지운 댓글은 세지 않는다 */
    private Map<Long, Long> commentCounts(List<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                SELECT notice_id, COUNT(*) AS n FROM notice_comment
                WHERE notice_id IN (:ids) AND deleted_at IS NULL
                GROUP BY notice_id
                """, Map.of("ids", ids), rs -> {
                    counts.put(rs.getLong("notice_id"), rs.getLong("n"));
                });
        return counts;
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getLong("id"), rs.getString("title"), rs.getBoolean("is_pinned"),
                rs.getBoolean("is_must_read"), rs.getObject("due_date", LocalDate.class),
                new OpsNoticeResponse.Person(rs.getLong("author_id"), rs.getString("nickname")),
                instant(rs, "reminded_at"), instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record Row(long id, String title, boolean pinned, boolean mustRead, LocalDate dueDate,
                       OpsNoticeResponse.Person author, Instant remindedAt, Instant createdAt) {
    }

}
