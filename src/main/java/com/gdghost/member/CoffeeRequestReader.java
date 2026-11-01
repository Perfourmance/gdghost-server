package com.gdghost.member;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 내가 그 사람에게 보낸 마지막 신청. 프로필과 커피챗 신청이 같은 기준으로 본다 */
@Repository
public class CoffeeRequestReader {

    private static final Duration COOLDOWN = Duration.ofDays(14);

    private final JdbcTemplate jdbc;

    public CoffeeRequestReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Latest(String state, Instant respondedAt) {

        public boolean isPending() {
            return state.equals("REQUESTED");
        }

        /** 거절 · 만료 후 14일 안이면 다시 신청할 수 있는 시각, 아니면 null */
        public Instant cooldownUntil(Instant now) {
            if (respondedAt == null || !(state.equals("DECLINED") || state.equals("EXPIRED"))) {
                return null;
            }
            Instant until = respondedAt.plus(COOLDOWN);
            return now.isBefore(until) ? until : null;
        }

    }

    public Optional<Latest> latest(long requesterId, long receiverId) {
        return jdbc.query("""
                SELECT state, responded_at FROM coffee_chat
                WHERE requester_id = ? AND receiver_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, (rs, i) -> {
            LocalDateTime respondedAt = rs.getObject("responded_at", LocalDateTime.class);
            return new Latest(rs.getString("state"), respondedAt == null ? null : respondedAt.toInstant(ZoneOffset.UTC));
        }, requesterId, receiverId).stream().findFirst();
    }

}
