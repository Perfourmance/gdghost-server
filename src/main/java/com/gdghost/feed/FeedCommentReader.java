package com.gdghost.feed;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.security.Role;
import com.gdghost.question.AuthorRef;

@Repository
class FeedCommentReader {

    private static final String SELECT = """
            SELECT c.id, c.parent_id, c.depth, c.body, c.deleted_by, c.created_at,
                   c.author_id, m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn
            FROM feed_comment c JOIN member m ON m.id = c.author_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    FeedCommentReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Row(long id, Long parentId, int depth, AuthorRef.Row author, String body, String deletedBy,
            Instant createdAt) {
    }

    /** 오래된 순. 지운 댓글은 지우지 않은 답글이 있을 때만 자리가 남는다 */
    List<Row> comments(long postId, Long afterId, int limit) {
        return jdbc.query(SELECT + """
                WHERE c.post_id = :post AND c.parent_id IS NULL
                  AND (c.deleted_at IS NULL OR EXISTS (
                        SELECT 1 FROM feed_comment r WHERE r.parent_id = c.id AND r.deleted_at IS NULL))
                  AND (:after IS NULL OR c.id > :after)
                ORDER BY c.id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("post", postId)
                .addValue("after", afterId)
                .addValue("limit", limit), (rs, i) -> row(rs));
    }

    /** 지우지 않은 답글, 오래된 순 */
    List<Row> replies(Collection<Long> commentIds) {
        return jdbc.query(SELECT + """
                WHERE c.parent_id IN (:ids) AND c.deleted_at IS NULL
                ORDER BY c.id
                """, Map.of("ids", commentIds), (rs, i) -> row(rs));
    }

    Row find(long commentId) {
        return jdbc.queryForObject(SELECT + "WHERE c.id = :id", Map.of("id", commentId), (rs, i) -> row(rs));
    }

    private static Row row(ResultSet rs) throws SQLException {
        AuthorRef.Row author = new AuthorRef.Row(rs.getLong("author_id"), rs.getString("nickname"),
                rs.getInt("generation"), Role.valueOf(rs.getString("role")), rs.getBoolean("withdrawn"));
        return new Row(rs.getLong("id"), rs.getObject("parent_id", Long.class), rs.getInt("depth"), author,
                rs.getString("body"), rs.getString("deleted_by"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

}
