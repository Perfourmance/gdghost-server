package com.gdghost.common.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.error.ErrorLogRecorder;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class BatchRunnerTest {

    private static final String SECRET = "hong@gmail.com";

    @Autowired
    BatchRunner runner;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeDiscordGateway discord;

    @BeforeEach
    void clear() {
        discord.clear();
    }

    @Test
    void 성공하면_요약을_SUCCESS로_남기고_알리지_않는다() {
        runner.run("test_job", () -> "deleted=3");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM batch_log WHERE job_name = 'test_job'");
        assertThat(row.get("state")).isEqualTo("SUCCESS");
        assertThat(row.get("message")).isEqualTo("deleted=3");
        assertThat(row.get("started_at")).isNotNull();
        assertThat(row.get("ended_at")).isNotNull();
        assertThat(discord.after(Duration.ofMillis(200), s -> s.is(DiscordChannel.DEV, "배치 실패")))
                .isEmpty();
    }

    @Test
    void 실패하면_던지지_않고_FAILED와_에러와_디스코드를_남긴다() {
        runner.run("test_job", () -> {
            throw new IllegalStateException(SECRET);
        });

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM batch_log WHERE job_name = 'test_job'");
        assertThat(row.get("state")).isEqualTo("FAILED");
        assertThat(row.get("message")).isEqualTo(IllegalStateException.class.getName());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM error_log WHERE path = 'batch test_job'", Integer.class))
                .isEqualTo(1);

        FakeDiscordGateway.Sent sent = discord.await(s -> s.is(DiscordChannel.DEV, "배치 실패"));
        assertThat(sent.message().description()).contains("test\\_job").doesNotContain(SECRET);
    }

    @Test
    void 같은_실패가_연달아_나도_디스코드는_매번_간다() {
        for (int i = 0; i < 2; i++) {
            runner.run("test_job", () -> {
                throw new IllegalStateException("x");
            });
        }

        List<FakeDiscordGateway.Sent> sent = discord.await(s -> s.is(DiscordChannel.DEV, "배치 실패"), 2);
        assertThat(sent).hasSize(2);
        // 같은 에러 1시간 1회 알림이 함께 나가지 않는다
        assertThat(discord.after(Duration.ofMillis(200), s -> s.is(DiscordChannel.DEV, "에러"))).isEmpty();
    }

    @Test
    void 긴_요약은_컬럼_길이에_맞춰_자른다() {
        runner.run("test_job", () -> "x".repeat(1500));

        assertThat(jdbc.queryForObject("SELECT CHAR_LENGTH(message) FROM batch_log", Integer.class)).isEqualTo(1000);
    }

    @Test
    void batch_log를_못_써도_실패_알림은_나간다() {
        JdbcTemplate broken = mock(JdbcTemplate.class);
        when(broken.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("down"));
        ErrorLogRecorder errorLog = mock(ErrorLogRecorder.class);
        DiscordNotifier notifier = mock(DiscordNotifier.class);
        BatchRunner isolated = new BatchRunner(broken, Clock.systemUTC(), errorLog, notifier);

        isolated.run("test_job", () -> {
            throw new IllegalStateException("x");
        });

        verify(notifier).send(eq(DiscordChannel.DEV), any(DiscordMessage.class));
    }

    @Test
    void 시작_시각은_끝_시각보다_늦지_않다() {
        runner.run("test_job", () -> "ok");

        Map<String, Object> row = jdbc.queryForMap("SELECT started_at, ended_at FROM batch_log");
        assertThat((LocalDateTime) row.get("started_at")).isBeforeOrEqualTo((LocalDateTime) row.get("ended_at"));
    }

}
