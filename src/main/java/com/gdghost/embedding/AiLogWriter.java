package com.gdghost.embedding;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** ai_log 한 줄. 내용은 남기지 않는다 */
@Repository
public class AiLogWriter {

    public enum Kind {
        EMBED_QUESTION, EMBED_MEMBER, FIND_SENIOR
    }

    public enum State {
        SUCCESS, FAILED, FALLBACK
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiLogWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(Kind kind, Long targetId, String model, Integer latencyMs, State state) {
        jdbc.update("""
                INSERT INTO ai_log (kind, target_id, model, latency_ms, state, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, kind.name(), targetId, model, latencyMs, state.name(), LocalDateTime.now(clock));
    }

}
