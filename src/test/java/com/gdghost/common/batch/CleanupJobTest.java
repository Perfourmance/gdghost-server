package com.gdghost.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class CleanupJobTest {

    @Autowired
    CleanupJob job;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Fixtures fixtures;

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void 알림은_90일이_지나면_읽음과_상관없이_지운다() {
        long member = fixtures.member().id();
        notification(member, "OLD_UNREAD", now().minusDays(91), null);
        notification(member, "OLD_READ", now().minusDays(91), now().minusDays(90));
        notification(member, "RECENT", now().minusDays(89), null);

        job.run();

        assertThat(jdbc.queryForList("SELECT type FROM notification", String.class)).containsExactly("RECENT");
    }

    @Test
    void 에러_로그는_마지막_발생이_6개월_지나면_지운다() {
        errorLog("a".repeat(64), now().minusMonths(7));
        errorLog("b".repeat(64), now().minusMonths(5));

        job.run();

        assertThat(jdbc.queryForList("SELECT fingerprint FROM error_log", String.class))
                .containsExactly("b".repeat(64));
    }

    @Test
    void 배치_로그는_10분_배치_성공만_30일_그_밖은_90일() {
        batchLog("routing_10min", "SUCCESS", "routing ok 31", now().minusDays(31));
        batchLog("routing_10min", "SUCCESS", "routing ok 29", now().minusDays(29));
        batchLog("routing_10min", "FAILED", "routing failed 31", now().minusDays(31));
        batchLog("routing_10min", "FAILED", "routing failed 91", now().minusDays(91));
        batchLog("deadline_daily_09", "SUCCESS", "other 31", now().minusDays(31));
        batchLog("deadline_daily_09", "SUCCESS", "other 91", now().minusDays(91));

        job.run();

        List<String> left = jdbc.queryForList("SELECT message FROM batch_log WHERE job_name <> ?", String.class,
                CleanupJob.NAME);
        assertThat(left).containsExactlyInAnyOrder("routing ok 29", "routing failed 31", "other 31");
    }

    @Test
    void AI_로그는_1년이_지나면_지운다() {
        aiLog(1L, now().minusYears(1).minusDays(1));
        aiLog(2L, now().minusYears(1).plusDays(1));

        job.run();

        assertThat(jdbc.queryForList("SELECT target_id FROM ai_log", Long.class)).containsExactly(2L);
    }

    @Test
    void 지운_개수를_요약으로_남긴다() {
        long member = fixtures.member().id();
        notification(member, "OLD", now().minusDays(91), null);
        errorLog("a".repeat(64), now().minusMonths(7));

        job.run();

        assertThat(jdbc.queryForObject("SELECT message FROM batch_log WHERE job_name = ? AND state = 'SUCCESS'",
                String.class, CleanupJob.NAME)).isEqualTo("notification=1 error_log=1 batch_log=0 ai_log=0");
    }

    private void notification(long memberId, String type, LocalDateTime createdAt, LocalDateTime readAt) {
        jdbc.update("""
                INSERT INTO notification (member_id, type, title, target_type, target_id, read_at, created_at)
                VALUES (?, ?, '제목', 'QUESTION', 1, ?, ?)
                """, memberId, type, readAt, createdAt);
    }

    private void errorLog(String fingerprint, LocalDateTime lastAt) {
        jdbc.update("""
                INSERT INTO error_log (fingerprint, count, first_at, last_at) VALUES (?, 1, ?, ?)
                """, fingerprint, lastAt, lastAt);
    }

    private void batchLog(String jobName, String state, String message, LocalDateTime startedAt) {
        jdbc.update("""
                INSERT INTO batch_log (job_name, state, message, started_at, ended_at) VALUES (?, ?, ?, ?, ?)
                """, jobName, state, message, startedAt, startedAt);
    }

    private void aiLog(long targetId, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO ai_log (kind, target_id, state, created_at) VALUES ('EMBED_QUESTION', ?, 'SUCCESS', ?)
                """, targetId, createdAt);
    }

}
