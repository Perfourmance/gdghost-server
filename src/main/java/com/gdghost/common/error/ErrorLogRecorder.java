package com.gdghost.common.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.security.TokenHasher;

/**
 * 처리하지 못한 에러를 error_log에 모은다. 같은 에러는 한 줄에 count만 올린다.
 * 같은 에러 = 예외 종류 + 처음 만나는 우리 코드 줄.
 * 개발자 디스코드에는 같은 에러를 1시간에 한 번만 알린다. 메시지는 개인정보가 섞일 수 있어 보내지 않는다.
 */
@Component
public class ErrorLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogRecorder.class);
    private static final String APP_PACKAGE = "com.gdghost.";
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_STACK = 60_000;
    private static final int MAX_PATH = 500;
    private static final Duration ALERT_INTERVAL = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;
    private final Clock clock;
    // 디스코드 쪽도 실패를 여기에 기록하므로 필요할 때 꺼낸다
    private final ObjectProvider<DiscordNotifier> discord;

    public ErrorLogRecorder(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
            ObjectProvider<DiscordNotifier> discord) {
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(transactionManager);
        // 원래 작업이 롤백돼도 에러 기록은 남게
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.discord = discord;
    }

    /** @param path 요청 경로, 비동기면 "async 클래스.메서드" */
    public void record(Throwable e, String path) {
        record(e, path, true);
    }

    /** 개발자 디스코드에 알리지 않는다 — 디스코드 전송 실패용 */
    public void recordQuietly(Throwable e, String path) {
        record(e, path, false);
    }

    private void record(Throwable e, String path, boolean alert) {
        LocalDateTime now = LocalDateTime.now(clock);
        String fingerprint = fingerprint(e);
        String trimmedPath = truncate(path, MAX_PATH);
        Integer count;
        try {
            count = newTransaction.execute(status -> {
                jdbc.update("""
                        INSERT INTO error_log (fingerprint, message, stack, path, count, first_at, last_at)
                        VALUES (?, ?, ?, ?, 1, ?, ?) AS new
                        ON DUPLICATE KEY UPDATE count = error_log.count + 1, message = new.message,
                                                path = new.path, last_at = new.last_at
                        """, fingerprint, truncate(e.toString(), MAX_MESSAGE), truncate(stackOf(e), MAX_STACK),
                        trimmedPath, now, now);
                if (!alert) {
                    return null;
                }
                // 조건부 UPDATE라 여러 스레드가 같은 에러를 동시에 만나도 한 번만 알린다
                int claimed = jdbc.update("""
                        UPDATE error_log SET alerted_at = ?
                        WHERE fingerprint = ? AND (alerted_at IS NULL OR alerted_at <= ?)
                        """, now, fingerprint, now.minus(ALERT_INTERVAL));
                return claimed == 0 ? null
                        : jdbc.queryForObject("SELECT count FROM error_log WHERE fingerprint = ?", Integer.class,
                                fingerprint);
            });
        } catch (RuntimeException failure) {
            // DB가 죽어도 원래 에러 응답은 나가야 함
            log.warn("error_log 기록 실패", failure);
            return;
        }
        if (count != null) {
            alert(e, trimmedPath, count);
        }
    }

    private void alert(Throwable e, String path, int count) {
        StackTraceElement frame = appFrame(e);
        String description = "종류 " + e.getClass().getName()
                + "\n위치 " + (frame == null ? "-" : frame)
                + "\n경로 " + (path == null ? "-" : path)
                + "\n누적 " + count + "회";
        discord.getObject().send(DiscordChannel.DEV, new DiscordMessage("에러", null,
                DiscordMessage.escape(description)));
    }

    static String fingerprint(Throwable e) {
        return TokenHasher.sha256(e.getClass().getName() + "@" + appFrame(e));
    }

    private static StackTraceElement appFrame(Throwable e) {
        StackTraceElement[] stack = e.getStackTrace();
        return Arrays.stream(stack)
                .filter(f -> f.getClassName().startsWith(APP_PACKAGE))
                .findFirst()
                .orElse(stack.length > 0 ? stack[0] : null);
    }

    private static String stackOf(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        // 이모지(서로게이트 쌍)를 반으로 자르면 utf8mb4 저장이 실패함
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }

}
