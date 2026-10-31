package com.gdghost.feed;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.security.Role;
import com.gdghost.question.AuthorRef;

@Repository
class FeedReader {

    private static final String SELECT = """
            SELECT p.id, p.category, p.body, p.like_count, p.created_at,
                   p.author_id, m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn
            FROM feed_post p JOIN member m ON m.id = p.author_id
            WHERE p.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    FeedReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long id, FeedCategory category, AuthorRef.Row author, String body, long likeCount, Instant createdAt) {
    }

    List<Row> list(FeedCategory category, Long beforeId, int limit) {
        return jdbc.query(SELECT + """
                  AND (:category IS NULL OR p.category = :category)
                  AND (:before IS NULL OR p.id < :before)
                ORDER BY p.id DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("category", category == null ? null : category.name())
                .addValue("before", beforeId)
                .addValue("limit", limit), (rs, i) -> row(rs));
    }

    Optional<Row> find(long postId) {
        return jdbc.query(SELECT + "  AND p.id = :id\n", Map.of("id", postId), (rs, i) -> row(rs)).stream().findFirst();
    }

    Set<Long> likedAmong(Collection<Long> postIds, long memberId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT post_id FROM feed_like WHERE post_id IN (:ids) AND member_id = :member",
                new MapSqlParameterSource().addValue("ids", postIds).addValue("member", memberId), Long.class));
    }

    Map<Long, Long> commentCounts(Collection<Long> postIds) {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT post_id, COUNT(*) AS n FROM feed_comment
                WHERE post_id IN (:ids) AND deleted_at IS NULL
                GROUP BY post_id
                """, Map.of("ids", postIds), rs -> {
                    counts.put(rs.getLong("post_id"), rs.getLong("n"));
                });
        return counts;
    }

    private static Row row(ResultSet rs) throws SQLException {
        AuthorRef.Row author = new AuthorRef.Row(rs.getLong("author_id"), rs.getString("nickname"),
                rs.getInt("generation"), Role.valueOf(rs.getString("role")), rs.getBoolean("withdrawn"));
        return new Row(rs.getLong("id"), FeedCategory.valueOf(rs.getString("category")), author, rs.getString("body"),
                rs.getLong("like_count"), rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

}
