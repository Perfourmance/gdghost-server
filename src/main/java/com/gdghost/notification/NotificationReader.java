package com.gdghost.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationReader {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 최신순. afterId가 있으면 그보다 오래된 것 */
    List<NotificationResponse> list(long memberId, Long afterId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("afterId", afterId)
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT id, type, title, body, target_type, target_id, sub_target_type, sub_target_id,
                       read_at, created_at
                FROM notification
                WHERE member_id = :memberId AND (:afterId IS NULL OR id < :afterId)
                ORDER BY id DESC
                LIMIT :limit
                """, params, NotificationReader::row);
    }

    public int unreadCount(long memberId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification WHERE member_id = :memberId AND read_at IS NULL
                """, Map.of("memberId", memberId), Integer.class);
    }

    private static NotificationResponse row(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationResponse(rs.getLong("id"), rs.getString("type"), rs.getString("title"),
                rs.getString("body"),
                new NotificationTarget(rs.getString("target_type"), rs.getLong("target_id"),
                        rs.getString("sub_target_type"), rs.getObject("sub_target_id", Long.class)),
                rs.getObject("read_at", LocalDateTime.class) != null,
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

}
