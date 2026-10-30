package com.gdghost.common.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.error.ErrorLogRecorder;

/**
 * 배치 실행 틀. 결과를 batch_log에 한 줄 남기고, 실패는 개발자 디스코드로 매번 알린다.
 * 작업이 롤백돼도 기록은 남아야 하므로 트랜잭션으로 감싸지 않는다.
 */
@Component
public class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);
    private static final int MAX_MESSAGE = 1000;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ErrorLogRecorder errorLog;
    private final DiscordNotifier discord;

    public BatchRunner(JdbcTemplate jdbc, Clock clock, ErrorLogRecorder errorLog, DiscordNotifier discord) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.errorLog = errorLog;
        this.discord = discord;
    }

    /** @param job 성공하면 batch_log에 남길 요약을 돌려준다 */
    public void run(String jobName, Supplier<String> job) {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        try {
            String summary = job.get();
            write(jobName, "SUCCESS", summary, startedAt);
        } catch (RuntimeException e) {
            log.error("배치 실패: {}", jobName, e);
            // 예외 메시지에는 개인정보가 섞일 수 있어 종류만. 스택은 error_log에
            String kind = e.getClass().getName();
            write(jobName, "FAILED", kind, startedAt);
            errorLog.recordQuietly(e, "batch " + jobName);
            discord.send(DiscordChannel.DEV, new DiscordMessage("배치 실패", null,
                    DiscordMessage.escape("배치 " + jobName + "\n종류 " + kind)));
        }
    }

    private void write(String jobName, String state, String message, LocalDateTime startedAt) {
        try {
            jdbc.update("""
                    INSERT INTO batch_log (job_name, state, message, started_at, ended_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, jobName, state, truncate(message), startedAt, LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            // DB 장애여도 실패 알림은 나가야 함
            log.warn("batch_log 기록 실패: {} {}", jobName, state, e);
        }
    }

    private static String truncate(String message) {
        return message == null || message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
    }

}
