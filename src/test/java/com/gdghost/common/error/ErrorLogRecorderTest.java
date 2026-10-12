package com.gdghost.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.support.IntegrationTest;

@IntegrationTest
class ErrorLogRecorderTest {

    @Autowired
    ErrorLogRecorder errorLog;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 같은_에러는_한_줄에_횟수만_올린다() {
        errorLog.record(boom("첫 번째"), "/api/v1/a");
        errorLog.record(boom("두 번째"), "/api/v1/b");

        Map<String, Object> row = jdbc.queryForMap("SELECT count, message, path, stack FROM error_log");
        assertThat(row).containsEntry("count", 2)
                .containsEntry("message", "java.lang.IllegalStateException: 두 번째")
                .containsEntry("path", "/api/v1/b");
        assertThat((String) row.get("stack")).contains("ErrorLogRecorderTest.boom");
    }

    @Test
    void 다른_곳에서_난_에러는_따로_묶는다() {
        errorLog.record(boom("같은 메시지"), "/api/v1/a");
        errorLog.record(new IllegalStateException("같은 메시지"), "/api/v1/a");
        errorLog.record(new IllegalArgumentException("같은 메시지"), "/api/v1/a");

        assertThat(rows()).isEqualTo(3);
    }

    @Test
    void 원래_작업이_롤백돼도_남는다() {
        tx.executeWithoutResult(status -> {
            errorLog.record(boom("롤백"), "/api/v1/a");
            status.setRollbackOnly();
        });

        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void 긴_메시지는_잘라서_남긴다() {
        errorLog.record(boom("가".repeat(2000)), "/api/v1/a");

        assertThat(jdbc.queryForObject("SELECT CHAR_LENGTH(message) FROM error_log", Integer.class))
                .isEqualTo(1000);
    }

    private static RuntimeException boom(String message) {
        return new IllegalStateException(message);
    }

    private long rows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM error_log", Long.class);
    }

}
