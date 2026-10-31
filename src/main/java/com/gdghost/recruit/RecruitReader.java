package com.gdghost.recruit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RecruitReader {

    private static final String SELECT = """
            SELECT r.id, r.kind, r.title, r.meet_at, r.capacity, r.joined_count, r.memo, r.author_id,
                   r.state = 'OPEN' AND r.meet_at > :now AS is_open,
                   rm.member_id IS NOT NULL AS joined,
                   IF(m.withdrawn_at IS NULL, m.nickname, NULL) AS nickname
            FROM recruit r
            JOIN member m ON m.id = r.author_id
            LEFT JOIN recruit_member rm ON rm.recruit_id = r.id AND rm.member_id = :viewer
            """;

    private final NamedParameterJdbcTemplate jdbc;

    RecruitReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 위치 (모집중 여부, meet_at, id) */
    record Cursor(boolean open, LocalDateTime meetAt, long id) {
    }

    /** 모집중 먼저, 그 안에서 만나는 시각이 가까운 순. 만나는 시각이 7일 넘게 지난 모집은 빠진다 */
    List<RecruitSummary> list(long viewerId, RecruitKind kind, Instant now, Cursor after, int limit) {
        LocalDateTime nowUtc = utc(now);
        return jdbc.query(SELECT + """
                WHERE r.meet_at >= :oldest
                  AND (:kind IS NULL OR r.kind = :kind)
                  AND (:afterId IS NULL
                       OR (r.state = 'OPEN' AND r.meet_at > :now) < :afterOpen
                       OR ((r.state = 'OPEN' AND r.meet_at > :now) = :afterOpen
                           AND (r.meet_at > :afterMeet OR (r.meet_at = :afterMeet AND r.id > :afterId))))
                ORDER BY is_open DESC, r.meet_at, r.id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("now", nowUtc)
                .addValue("viewer", viewerId)
                .addValue("oldest", nowUtc.minusDays(7))
                .addValue("kind", kind == null ? null : kind.name())
                .addValue("afterOpen", after == null ? null : after.open())
                .addValue("afterMeet", after == null ? null : after.meetAt())
                .addValue("afterId", after == null ? null : after.id())
                .addValue("limit", limit), (rs, i) -> summary(rs, viewerId));
    }

    Optional<RecruitSummary> find(long recruitId, long viewerId, Instant now) {
        return jdbc.query(SELECT + "WHERE r.id = :id", new MapSqlParameterSource()
                .addValue("now", utc(now))
                .addValue("viewer", viewerId)
                .addValue("id", recruitId), (rs, i) -> summary(rs, viewerId)).stream().findFirst();
    }

    List<RecruitPerson> participants(long recruitId) {
        return jdbc.query("""
                SELECT m.id, IF(m.withdrawn_at IS NULL, m.nickname, NULL) AS nickname
                FROM recruit_member rm
                JOIN member m ON m.id = rm.member_id
                WHERE rm.recruit_id = :id
                ORDER BY rm.joined_at, m.id
                """, Map.of("id", recruitId), (rs, i) -> new RecruitPerson(rs.getLong("id"), rs.getString("nickname")));
    }

    boolean scheduleExists(long scheduleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM schedule WHERE id = :id AND deleted_at IS NULL)
                """, Map.of("id", scheduleId), Boolean.class));
    }

    boolean joined(long recruitId, long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM recruit_member WHERE recruit_id = :recruit AND member_id = :member)
                """, Map.of("recruit", recruitId, "member", memberId), Boolean.class));
    }

    private static RecruitSummary summary(ResultSet rs, long viewerId) throws SQLException {
        long authorId = rs.getLong("author_id");
        return new RecruitSummary(rs.getLong("id"), RecruitKind.valueOf(rs.getString("kind")), rs.getString("title"),
                rs.getObject("meet_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), rs.getInt("capacity"),
                rs.getInt("joined_count"), rs.getBoolean("is_open") ? RecruitState.OPEN : RecruitState.CLOSED,
                rs.getBoolean("joined"), authorId == viewerId, new RecruitPerson(authorId, rs.getString("nickname")),
                rs.getString("memo"));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

}
