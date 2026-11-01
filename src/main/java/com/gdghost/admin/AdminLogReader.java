package com.gdghost.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.common.page.CursorPage;
import com.gdghost.embedding.AiLogWriter;

/** 배치 · AI 로그. 최신순, 커서는 id. 요약은 목록 필터와 상관없이 전체 기준 */
@Repository
public class AdminLogReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AdminLogReader(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public AdminLogResponse.Batches batches(String jobName, BatchLogState state, String cursor, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", size + 1);
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (jobName != null && !jobName.isBlank()) {
            where.append(" AND job_name = :jobName");
            params.addValue("jobName", jobName.strip());
        }
        if (state != null) {
            where.append(" AND state = :state");
            params.addValue("state", state.name());
        }
        addCursor(where, params, cursor);
        List<AdminLogResponse.Batch> items = jdbc.query("""
                SELECT id, job_name, state, message, started_at, ended_at
                FROM batch_log
                """ + where + """

                ORDER BY id DESC
                LIMIT :limit
                """, params, (rs, i) -> {
            Instant startedAt = instant(rs.getObject("started_at", LocalDateTime.class));
            Instant endedAt = instant(rs.getObject("ended_at", LocalDateTime.class));
            return new AdminLogResponse.Batch(rs.getLong("id"), rs.getString("job_name"), rs.getString("state"),
                    rs.getString("message"), startedAt, endedAt,
                    endedAt == null ? null : Duration.between(startedAt, endedAt).toMillis());
        });
        return AdminLogResponse.Batches.of(CursorPage.of(items, size, item -> String.valueOf(item.id())),
                new AdminLogResponse.BatchSummary(failedSince(Duration.ofHours(24))));
    }

    public AdminLogResponse.AiLogs aiLogs(AiLogWriter.Kind kind, AiLogWriter.State state, String cursor, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", size + 1);
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (kind != null) {
            where.append(" AND kind = :kind");
            params.addValue("kind", kind.name());
        }
        if (state != null) {
            where.append(" AND state = :state");
            params.addValue("state", state.name());
        }
        addCursor(where, params, cursor);
        List<AdminLogResponse.AiLog> items = jdbc.query("""
                SELECT id, kind, target_id, model, latency_ms, state, created_at
                FROM ai_log
                """ + where + """

                ORDER BY id DESC
                LIMIT :limit
                """, params, (rs, i) -> new AdminLogResponse.AiLog(rs.getLong("id"), rs.getString("kind"),
                rs.getObject("target_id", Long.class), rs.getString("model"),
                rs.getObject("latency_ms", Integer.class), rs.getString("state"),
                instant(rs.getObject("created_at", LocalDateTime.class))));
        return AdminLogResponse.AiLogs.of(new AdminLogResponse.AiSummary(aiPeriod(Duration.ofDays(7))),
                CursorPage.of(items, size, item -> String.valueOf(item.id())));
    }

    private long failedSince(Duration period) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM batch_log WHERE state = 'FAILED' AND started_at >= :since
                """, Map.of("since", since(period)), Long.class);
    }

    private AdminLogResponse.AiPeriod aiPeriod(Duration period) {
        LocalDateTime since = since(period);
        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(CASE WHEN state = 'SUCCESS' THEN 1 END) AS success,
                       COUNT(CASE WHEN state = 'FALLBACK' THEN 1 END) AS fallback
                FROM ai_log
                WHERE created_at >= :since
                """, Map.of("since", since));
        long total = ((Number) counts.get("total")).longValue();
        long success = ((Number) counts.get("success")).longValue();
        long fallback = ((Number) counts.get("fallback")).longValue();
        return new AdminLogResponse.AiPeriod(total, rate(success, total), rate(fallback, total),
                p95Latency(since));
    }

    /** 상위 5% 경계의 실제 값(nearest rank) */
    private Integer p95Latency(LocalDateTime since) {
        Long measured = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_log
                WHERE created_at >= :since AND state = 'SUCCESS' AND latency_ms IS NOT NULL
                """, Map.of("since", since), Long.class);
        if (measured == 0) {
            return null;
        }
        long offset = (long) Math.ceil(measured * 0.95) - 1;
        return jdbc.queryForObject("""
                SELECT latency_ms FROM ai_log
                WHERE created_at >= :since AND state = 'SUCCESS' AND latency_ms IS NOT NULL
                ORDER BY latency_ms
                LIMIT 1 OFFSET :offset
                """, Map.of("since", since, "offset", offset), Integer.class);
    }

    private LocalDateTime since(Duration period) {
        return LocalDateTime.ofInstant(clock.instant().minus(period), ZoneOffset.UTC);
    }

    private static void addCursor(StringBuilder where, MapSqlParameterSource params, String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return;
        }
        try {
            params.addValue("cursorId", Long.parseLong(raw));
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
        where.append(" AND id < :cursorId");
    }

    private static double rate(long part, long total) {
        return total == 0 ? 0 : (double) part / total;
    }

    private static Instant instant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

}
