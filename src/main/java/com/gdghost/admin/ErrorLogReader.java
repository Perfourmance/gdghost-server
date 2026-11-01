package com.gdghost.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;

/** 마지막 발생이 최근인 순. 커서는 (last_at, id) */
@Repository
public class ErrorLogReader {

    private final NamedParameterJdbcTemplate jdbc;

    public ErrorLogReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CursorPage<ErrorLogResponse.Item> list(String query, Instant since, String cursor, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", size + 1);
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (query != null && !query.isBlank()) {
            where.append(" AND (message LIKE :like ESCAPE '!' OR path LIKE :like ESCAPE '!')");
            params.addValue("like", like(query.strip()));
        }
        if (since != null) {
            where.append(" AND last_at >= :since");
            params.addValue("since", utc(since));
        }
        String raw = CursorPage.decode(cursor);
        if (raw != null) {
            where.append(" AND (last_at < :cursorAt OR (last_at = :cursorAt AND id < :cursorId))");
            addCursor(params, raw);
        }
        List<ErrorLogResponse.Item> items = jdbc.query("""
                SELECT id, fingerprint, message, path, count, first_at, last_at
                FROM error_log
                """ + where + """

                ORDER BY last_at DESC, id DESC
                LIMIT :limit
                """, params, (rs, i) -> new ErrorLogResponse.Item(rs.getLong("id"), rs.getString("fingerprint"),
                rs.getString("message"), rs.getString("path"), rs.getInt("count"), instant(rs.getObject("first_at",
                        LocalDateTime.class)), instant(rs.getObject("last_at", LocalDateTime.class))));
        return CursorPage.of(items, size, item -> item.lastAt() + "|" + item.id());
    }

    public ErrorLogResponse.Detail find(long id) {
        return jdbc.query("""
                SELECT id, fingerprint, message, path, count, first_at, last_at, stack
                FROM error_log
                WHERE id = :id
                """, Map.of("id", id), (rs, i) -> new ErrorLogResponse.Detail(rs.getLong("id"),
                rs.getString("fingerprint"), rs.getString("message"), rs.getString("path"), rs.getInt("count"),
                instant(rs.getObject("first_at", LocalDateTime.class)),
                instant(rs.getObject("last_at", LocalDateTime.class)), rs.getString("stack")))
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static void addCursor(MapSqlParameterSource params, String raw) {
        int separator = raw.indexOf('|');
        if (separator < 0) {
            throw CursorPage.invalidCursor();
        }
        try {
            params.addValue("cursorAt", utc(Instant.parse(raw.substring(0, separator))));
            params.addValue("cursorId", Long.parseLong(raw.substring(separator + 1)));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

    private static String like(String text) {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC);
    }

}
