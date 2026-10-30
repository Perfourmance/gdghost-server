package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class NoticeReminderReader {

    record Reminded(long noticeId, String title) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    NoticeReminderReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 활동 중 부원 중 확인했어요를 누르지 않은 사람 — 확인 집계(N/M)와 같은 기준 */
    List<Long> unconfirmedMembers(long noticeId) {
        return jdbc.queryForList("""
                SELECT m.id
                FROM member m
                LEFT JOIN notice_read r ON r.notice_id = :noticeId AND r.member_id = m.id
                WHERE m.withdrawn_at IS NULL AND m.suspended_at IS NULL AND r.confirmed_at IS NULL
                """, Map.of("noticeId", noticeId), Long.class);
    }

    /** 푸시를 미룬 공지. 지운 공지는 뺀다 */
    List<Reminded> remindedBetween(Instant from, Instant to) {
        return jdbc.query("""
                SELECT id, title FROM notice
                WHERE reminded_at >= :from AND reminded_at < :to AND deleted_at IS NULL
                """, Map.of("from", utc(from), "to", utc(to)),
                (rs, i) -> new Reminded(rs.getLong("id"), rs.getString("title")));
    }

    /** 재알림을 받았고 그사이에도 확인하지 않은 사람 */
    List<Long> stillUnconfirmedReceivers(long noticeId) {
        return jdbc.queryForList("""
                SELECT n.member_id
                FROM notification n
                LEFT JOIN notice_read r ON r.notice_id = n.target_id AND r.member_id = n.member_id
                WHERE n.type = 'NOTICE_REMIND' AND n.target_type = 'NOTICE' AND n.target_id = :noticeId
                  AND r.confirmed_at IS NULL
                """, Map.of("noticeId", noticeId), Long.class);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

}
