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

/** 부원 공지 목록 · 홈 고정 공지 · 미확인 필독 수. 읽음 · 확인은 보는 사람 기준, 지운 공지 제외 */
@Repository
public class NoticeListReader {

    private final NamedParameterJdbcTemplate jdbc;

    public NoticeListReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long id, String title, boolean pinned, boolean mustRead, LocalDate dueDate, NoticeDetail.Author author,
               boolean read, boolean confirmed, Instant createdAt) {
    }

    /** 위치 (is_pinned, id) */
    record Cursor(boolean pinned, long id) {
    }

    /** 고정 먼저, 그 안에서 최신순(id 내림차순). pinnedOnly면 고정 공지만 */
    List<Row> list(long memberId, boolean pinnedOnly, Cursor after, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("pinnedOnly", pinnedOnly)
                .addValue("afterPinned", after != null ? after.pinned() : null)
                .addValue("afterId", after != null ? after.id() : null)
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT n.id, n.title, n.is_pinned, n.is_must_read, n.due_date, n.created_at,
                       n.author_id, m.nickname, m.staff_title, m.withdrawn_at IS NOT NULL AS withdrawn,
                       r.member_id IS NOT NULL AS is_read, r.confirmed_at IS NOT NULL AS is_confirmed
                FROM notice n
                JOIN member m ON m.id = n.author_id
                LEFT JOIN notice_read r ON r.notice_id = n.id AND r.member_id = :memberId
                WHERE n.deleted_at IS NULL
                  AND (:pinnedOnly = FALSE OR n.is_pinned)
                  AND (:afterId IS NULL OR (n.is_pinned, n.id) < (:afterPinned, :afterId))
                ORDER BY n.is_pinned DESC, n.id DESC
                LIMIT :limit
                """, params, NoticeListReader::row);
    }

    List<GuestPinnedNotice> guestPinned(int limit) {
        return jdbc.query("""
                SELECT id, title FROM notice
                WHERE deleted_at IS NULL AND is_pinned
                ORDER BY id DESC
                LIMIT :limit
                """, Map.of("limit", limit), (rs, i) -> new GuestPinnedNotice(rs.getLong("id"), rs.getString("title")));
    }

    /** 마감일이 없거나 오늘(한국 날짜) 이후인 필독 공지 가운데 확인하지 않은 것 */
    long unconfirmedMustRead(long memberId, LocalDate today) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM notice n
                LEFT JOIN notice_read r ON r.notice_id = n.id AND r.member_id = :memberId
                WHERE n.deleted_at IS NULL AND n.is_must_read
                  AND (n.due_date IS NULL OR n.due_date >= :today)
                  AND r.confirmed_at IS NULL
                """, Map.of("memberId", memberId, "today", today), Long.class);
    }

    /** 지운 댓글은 세지 않는다 */
    Map<Long, Long> commentCounts(List<Long> ids) {
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
                NoticeDetail.Author.of(rs.getLong("author_id"), rs.getString("nickname"), rs.getString("staff_title"),
                        rs.getBoolean("withdrawn")),
                rs.getBoolean("is_read"), rs.getBoolean("is_confirmed"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

}
