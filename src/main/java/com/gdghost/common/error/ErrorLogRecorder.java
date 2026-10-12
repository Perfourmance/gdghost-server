package com.gdghost.common.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.security.TokenHasher;

/**
 * 처리하지 못한 에러를 error_log에 모은다. 같은 에러는 한 줄에 count만 올린다.
 * 같은 에러 = 예외 종류 + 처음 만나는 우리 코드 줄.
 */
@Component
public class ErrorLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogRecorder.class);
    private static final String APP_PACKAGE = "com.gdghost.";
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_STACK = 60_000;
    private static final int MAX_PATH = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    public ErrorLogRecorder(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(transactionManager);
        // 원래 작업이 롤백돼도 에러 기록은 남게
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** @param path 요청 경로, 비동기면 "async 클래스.메서드" */
    public void record(Throwable e, String path) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            newTransaction.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO error_log (fingerprint, message, stack, path, count, first_at, last_at)
                    VALUES (?, ?, ?, ?, 1, ?, ?) AS new
                    ON DUPLICATE KEY UPDATE count = error_log.count + 1, message = new.message,
                                            path = new.path, last_at = new.last_at
                    """, fingerprint(e), truncate(e.toString(), MAX_MESSAGE), truncate(stackOf(e), MAX_STACK),
                    truncate(path, MAX_PATH), now, now));
        } catch (RuntimeException failure) {
            // DB가 죽어도 원래 에러 응답은 나가야 함
            log.warn("error_log 기록 실패", failure);
        }
    }

    static String fingerprint(Throwable e) {
        StackTraceElement[] stack = e.getStackTrace();
        StackTraceElement frame = Arrays.stream(stack)
                .filter(f -> f.getClassName().startsWith(APP_PACKAGE))
                .findFirst()
                .orElse(stack.length > 0 ? stack[0] : null);
        return TokenHasher.sha256(e.getClass().getName() + "@" + frame);
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
