package com.gdghost.admin;

import java.time.Instant;
import java.util.List;

import com.gdghost.common.page.CursorPage;

public final class AdminLogResponse {

    private AdminLogResponse() {
    }

    public record Batches(List<Batch> items, BatchSummary summary, String nextCursor, boolean hasMore) {

        static Batches of(CursorPage<Batch> page, BatchSummary summary) {
            return new Batches(page.items(), summary, page.nextCursor(), page.hasMore());
        }
    }

    /** @param durationMs 끝난 시각이 없으면 null */
    public record Batch(long id, String jobName, String state, String message, Instant startedAt, Instant endedAt,
                        Long durationMs) {
    }

    public record BatchSummary(long last24hFailed) {
    }

    public record AiLogs(AiSummary summary, List<AiLog> items, String nextCursor, boolean hasMore) {

        static AiLogs of(AiSummary summary, CursorPage<AiLog> page) {
            return new AiLogs(summary, page.items(), page.nextCursor(), page.hasMore());
        }
    }

    public record AiLog(long id, String kind, Long targetId, String model, Integer latencyMs, String state,
                        Instant createdAt) {
    }

    public record AiSummary(AiPeriod last7d) {
    }

    /** @param p95LatencyMs 성공한 호출 기준. 없으면 null */
    public record AiPeriod(long total, double successRate, double fallbackRate, Integer p95LatencyMs) {
    }

}
