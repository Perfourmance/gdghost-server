package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 지우지 않은 공지 하나와 보는 사람의 확인 여부 */
@Repository
public class NoticeDetailReader {

    private final NamedParameterJdbcTemplate jdbc;

    public NoticeDetailReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> find(long noticeId) {
        return jdbc.query("""
                SELECT n.id, n.title, n.body, n.is_pinned, n.is_must_read, n.due_date, n.created_at,
                       n.author_id, m.nickname, m.staff_title, m.withdrawn_at IS NOT NULL AS withdrawn
                FROM notice n
                JOIN member m ON m.id = n.author_id
                WHERE n.id = :id AND n.deleted_at IS NULL
                """, Map.of("id", noticeId), (rs, i) -> new Row(rs.getLong("id"), rs.getString("title"),
                rs.getString("body"), rs.getBoolean("is_pinned"), rs.getBoolean("is_must_read"),
                rs.getObject("due_date", LocalDate.class),
                NoticeDetail.Author.of(rs.getLong("author_id"), rs.getString("nickname"), rs.getString("staff_title"),
                        rs.getBoolean("withdrawn")),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .stream().findFirst();
    }

    boolean confirmed(long noticeId, long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM notice_read
                               WHERE notice_id = :noticeId AND member_id = :memberId AND confirmed_at IS NOT NULL)
                """, Map.of("noticeId", noticeId, "memberId", memberId), Boolean.class));
    }

    record Row(long id, String title, String body, boolean pinned, boolean mustRead, LocalDate dueDate,
               NoticeDetail.Author author, Instant createdAt) {
    }

}
