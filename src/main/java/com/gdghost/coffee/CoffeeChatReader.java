package com.gdghost.coffee;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.member.MemberStatus;
import com.gdghost.question.AuthorRef;

@Repository
class CoffeeChatReader {

    private static final String SELECT = """
            SELECT c.id, c.state, c.purpose, c.created_at, c.responded_at, c.accept_memo,
                   m.id AS counterpart_id, m.nickname, m.generation, m.status, m.status_note,
                   m.withdrawn_at IS NOT NULL AS withdrawn
            FROM coffee_chat c
            JOIN member m ON m.id = IF(c.requester_id = :me, c.receiver_id, c.requester_id)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    CoffeeChatReader(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 최신순 */
    List<CoffeeChatItem> list(long me, CoffeeChatBox box, Long afterId, int limit) {
        String owner = box == CoffeeChatBox.RECEIVED ? "c.receiver_id" : "c.requester_id";
        Instant now = clock.instant();
        return jdbc.query(SELECT + "WHERE " + owner + """
                 = :me AND (:after IS NULL OR c.id < :after)
                ORDER BY c.id DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("me", me)
                .addValue("after", afterId)
                .addValue("limit", limit), (rs, i) -> item(rs, now));
    }

    CoffeeChatItem find(long me, long id) {
        Instant now = clock.instant();
        return jdbc.queryForObject(SELECT + "WHERE c.id = :id", Map.of("me", me, "id", id), (rs, i) -> item(rs, now));
    }

    private static CoffeeChatItem item(ResultSet rs, Instant now) throws SQLException {
        CoffeeChatState state = CoffeeChatState.valueOf(rs.getString("state"));
        Instant createdAt = instant(rs, "created_at");
        Instant respondedAt = instant(rs, "responded_at");
        boolean memoOpen = state == CoffeeChatState.ACCEPTED
                && now.isBefore(respondedAt.plus(CoffeeChat.MEMO_WINDOW));
        return new CoffeeChatItem(rs.getLong("id"), state, counterpart(rs), rs.getString("purpose"), createdAt,
                respondedAt, state == CoffeeChatState.REQUESTED ? createdAt.plus(CoffeeChat.RESPONSE_WINDOW) : null,
                memoOpen ? rs.getString("accept_memo") : null);
    }

    private static CoffeeChatItem.Counterpart counterpart(ResultSet rs) throws SQLException {
        if (rs.getBoolean("withdrawn")) {
            return new CoffeeChatItem.Counterpart(null, AuthorRef.WITHDRAWN_NICKNAME, null, null, null, true);
        }
        return new CoffeeChatItem.Counterpart(rs.getLong("counterpart_id"), rs.getString("nickname"),
                rs.getInt("generation"), MemberStatus.valueOf(rs.getString("status")), rs.getString("status_note"),
                false);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

}
