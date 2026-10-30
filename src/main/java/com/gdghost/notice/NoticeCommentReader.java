package com.gdghost.notice;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 공지 상세의 댓글. 지운 댓글(본인 삭제 · 신고 숨김)은 빼고 오래된 순, 전부 */
@Repository
public class NoticeCommentReader {

    private static final String SELECT = """
            SELECT c.id, c.body, c.created_at, c.author_id,
                   m.nickname, m.generation, m.withdrawn_at IS NOT NULL AS withdrawn
            FROM notice_comment c
            JOIN member m ON m.id = c.author_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public NoticeCommentReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<NoticeDetail.Comment> list(long noticeId, long viewerId) {
        return query(SELECT + """
                WHERE c.notice_id = :noticeId AND c.deleted_at IS NULL
                ORDER BY c.id
                """, new MapSqlParameterSource("noticeId", noticeId), viewerId);
    }

    NoticeDetail.Comment find(long commentId, long viewerId) {
        return query(SELECT + "WHERE c.id = :id", new MapSqlParameterSource("id", commentId), viewerId).getFirst();
    }

    private List<NoticeDetail.Comment> query(String sql, MapSqlParameterSource params, long viewerId) {
        return jdbc.query(sql, params, (rs, i) -> {
            long authorId = rs.getLong("author_id");
            return new NoticeDetail.Comment(rs.getLong("id"),
                    NoticeDetail.CommentAuthor.of(authorId, rs.getString("nickname"),
                            rs.getObject("generation", Integer.class), rs.getBoolean("withdrawn")),
                    rs.getString("body"), authorId == viewerId,
                    rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
        });
    }

}
