package com.gdghost.notice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 확인 집계. 여러 공지를 한 번에 센다 */
@Repository
public class NoticeStatsReader {

    private final NamedParameterJdbcTemplate jdbc;

    public NoticeStatsReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Map<Long, NoticeStats> stats(List<Long> noticeIds) {
        Map<Long, NoticeStats> stats = new HashMap<>();
        if (noticeIds.isEmpty()) {
            return stats;
        }
        long memberCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM member WHERE withdrawn_at IS NULL AND suspended_at IS NULL", Map.of(),
                Long.class);
        noticeIds.forEach(id -> stats.put(id, new NoticeStats(memberCount, 0, 0)));
        jdbc.query("""
                SELECT nr.notice_id, COUNT(*) AS read_count, COUNT(nr.confirmed_at) AS confirmed_count
                FROM notice_read nr
                JOIN member m ON m.id = nr.member_id
                WHERE nr.notice_id IN (:ids) AND m.withdrawn_at IS NULL AND m.suspended_at IS NULL
                GROUP BY nr.notice_id
                """, Map.of("ids", noticeIds), rs -> {
                    stats.put(rs.getLong("notice_id"), new NoticeStats(memberCount, rs.getLong("read_count"),
                            rs.getLong("confirmed_count")));
                });
        return stats;
    }

    NoticeStats stats(long noticeId) {
        return stats(List.of(noticeId)).get(noticeId);
    }

}
