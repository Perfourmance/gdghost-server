package com.gdghost.notice;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    public record UnconfirmedMustRead(long noticeId, String title, long confirmedCount, long memberCount) {
    }

    /** 마감일이 없거나 오늘(한국 날짜) 이후인 필독 공지 가운데 아직 다 확인하지 않은 것, 최신순 */
    public List<UnconfirmedMustRead> unconfirmedMustRead(LocalDate today, int limit) {
        Map<Long, String> titles = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, title FROM notice
                WHERE deleted_at IS NULL AND is_must_read AND (due_date IS NULL OR due_date >= :today)
                ORDER BY id DESC
                """, Map.of("today", today), rs -> {
                    titles.put(rs.getLong("id"), rs.getString("title"));
                });
        Map<Long, NoticeStats> stats = stats(List.copyOf(titles.keySet()));
        return titles.entrySet().stream()
                .filter(notice -> {
                    NoticeStats stat = stats.get(notice.getKey());
                    return stat.confirmedCount() < stat.memberCount();
                })
                .limit(limit)
                .map(notice -> new UnconfirmedMustRead(notice.getKey(), notice.getValue(),
                        stats.get(notice.getKey()).confirmedCount(), stats.get(notice.getKey()).memberCount()))
                .toList();
    }

    NoticeStats stats(long noticeId) {
        return stats(List.of(noticeId)).get(noticeId);
    }

}
