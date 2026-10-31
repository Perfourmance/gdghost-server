package com.gdghost.schedule;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ScheduleReader {

    private static final String COLUMNS = """
            SELECT s.id, s.kind, s.is_official, s.title, s.start_date, s.end_date, s.place, s.author_id,
                   IF(m.withdrawn_at IS NULL, m.nickname, NULL) AS nickname
            FROM schedule s
            JOIN member m ON m.id = s.author_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    ScheduleReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long id, ScheduleKind kind, boolean official, String title, LocalDate startDate, LocalDate endDate,
            String place, ScheduleAuthor author) {

        LocalDate deadline() {
            return endDate != null ? endDate : startDate;
        }
    }

    record Due(long id, String title, LocalDate deadline) {
    }

    /** 기간이 걸치는 일정 모두 */
    List<Row> between(LocalDate from, LocalDate to, ScheduleKind kind) {
        return jdbc.query(COLUMNS + """
                WHERE s.deleted_at IS NULL AND s.start_date <= :to AND COALESCE(s.end_date, s.start_date) >= :from
                  AND (:kind IS NULL OR s.kind = :kind)
                ORDER BY s.start_date, s.id
                """, new MapSqlParameterSource().addValue("from", from).addValue("to", to)
                .addValue("kind", kind == null ? null : kind.name()), ScheduleReader::row);
    }

    /** 마감이 지나지 않은 일정을 마감이 가까운 순으로 */
    List<Row> upcoming(LocalDate today, int limit) {
        return jdbc.query(COLUMNS + """
                WHERE s.deleted_at IS NULL AND COALESCE(s.end_date, s.start_date) >= :today
                ORDER BY COALESCE(s.end_date, s.start_date), s.id
                LIMIT :limit
                """, Map.of("today", today, "limit", limit), ScheduleReader::row);
    }

    ScheduleAuthor author(long authorId) {
        return jdbc.queryForObject("""
                SELECT id, IF(withdrawn_at IS NULL, nickname, NULL) AS nickname FROM member WHERE id = :id
                """, Map.of("id", authorId), (rs, i) -> new ScheduleAuthor(rs.getLong("id"), rs.getString("nickname")));
    }

    List<ScheduleDetail.RelatedRecruit> relatedRecruits(long scheduleId, Instant now) {
        return jdbc.query("""
                SELECT id, title, joined_count, capacity,
                       IF(state = 'OPEN' AND meet_at > :now AND joined_count < capacity, 'OPEN', 'CLOSED') AS state
                FROM recruit
                WHERE schedule_id = :id
                ORDER BY id
                """, new MapSqlParameterSource().addValue("id", scheduleId)
                .addValue("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)),
                (rs, i) -> new ScheduleDetail.RelatedRecruit(rs.getLong("id"), rs.getString("title"),
                        rs.getInt("joined_count"), rs.getInt("capacity"), rs.getString("state")));
    }

    List<Due> dueOn(List<LocalDate> dates) {
        return jdbc.query("""
                SELECT id, title, COALESCE(end_date, start_date) AS deadline
                FROM schedule
                WHERE deleted_at IS NULL AND COALESCE(end_date, start_date) IN (:dates)
                ORDER BY deadline, id
                """, Map.of("dates", dates), (rs, i) -> new Due(rs.getLong("id"), rs.getString("title"),
                        rs.getObject("deadline", LocalDate.class)));
    }

    /** 이 일정에 연결된 모집에 참여한 부원 */
    List<Long> participants(long scheduleId) {
        return jdbc.queryForList("""
                SELECT DISTINCT rm.member_id
                FROM recruit r
                JOIN recruit_member rm ON rm.recruit_id = r.id
                WHERE r.schedule_id = :id
                """, Map.of("id", scheduleId), Long.class);
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        return new Row(rs.getLong("id"), ScheduleKind.valueOf(rs.getString("kind")), rs.getBoolean("is_official"),
                rs.getString("title"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getString("place"),
                new ScheduleAuthor(rs.getLong("author_id"), rs.getString("nickname")));
    }

}
