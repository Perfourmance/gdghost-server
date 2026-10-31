package com.gdghost.common.batch;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보관 기간이 지난 알림 · 로그를 지운다. 중간에 실패하면 남은 것은 다음 날 지워진다 */
@Component
public class CleanupJob {

    static final String NAME = "cleanup_nightly";
    private static final String ROUTING_JOB = "routing_10min";
    private static final String RECRUIT_CLOSE_JOB = "recruit_close_10min";

    private final BatchRunner batch;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CleanupJob(BatchRunner batch, JdbcTemplate jdbc, Clock clock) {
        this.batch = batch;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void run() {
        batch.run(NAME, this::cleanup);
    }

    private String cleanup() {
        LocalDateTime now = LocalDateTime.now(clock);
        int notifications = jdbc.update("DELETE FROM notification WHERE created_at < ?", now.minusDays(90));
        int errors = jdbc.update("DELETE FROM error_log WHERE last_at < ?", now.minusMonths(6));
        int batches = jdbc.update("""
                DELETE FROM batch_log
                WHERE started_at < ?
                   OR (job_name IN (?, ?) AND state = 'SUCCESS' AND started_at < ?)
                """, now.minusDays(90), ROUTING_JOB, RECRUIT_CLOSE_JOB, now.minusDays(30));
        int ai = jdbc.update("DELETE FROM ai_log WHERE created_at < ?", now.minusYears(1));
        return "notification=%d error_log=%d batch_log=%d ai_log=%d".formatted(notifications, errors, batches, ai);
    }

}
