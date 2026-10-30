package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 읽음 · 확인 기록. 확인하면 읽음도 함께 남는다 */
@Repository
public class NoticeReadWriter {

    private final NamedParameterJdbcTemplate jdbc;

    public NoticeReadWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 이미 읽었으면 처음 시각을 그대로 둔다 */
    void read(long noticeId, long memberId, Instant now) {
        jdbc.update("""
                INSERT INTO notice_read (notice_id, member_id, read_at)
                VALUES (:noticeId, :memberId, :at)
                ON DUPLICATE KEY UPDATE read_at = read_at
                """, Map.of("noticeId", noticeId, "memberId", memberId,
                "at", LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
    }

    /** 이미 확인했으면 처음 시각을 그대로 둔다 */
    void confirm(long noticeId, long memberId, Instant now) {
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO notice_read (notice_id, member_id, read_at, confirmed_at)
                VALUES (:noticeId, :memberId, :at, :at) AS new
                ON DUPLICATE KEY UPDATE confirmed_at = COALESCE(notice_read.confirmed_at, new.confirmed_at)
                """, Map.of("noticeId", noticeId, "memberId", memberId, "at", at));
    }

}
