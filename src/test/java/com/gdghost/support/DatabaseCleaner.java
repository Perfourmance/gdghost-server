package com.gdghost.support;

import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 테스트마다 빈 DB에서 시작하게 한다.
 * 컨테이너를 모든 테스트가 같이 쓰므로 끝날 때가 아니라 시작할 때 비운다(앞 테스트가 실패해도 안전).
 */
public class DatabaseCleaner implements BeforeEachCallback {

    private static final List<String> KEEP = List.of("flyway_schema_history", "tag");

    @Override
    public void beforeEach(ExtensionContext context) {
        JdbcTemplate jdbc = SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE()
                """, String.class);

        // FOREIGN_KEY_CHECKS는 커넥션 단위 설정이라 한 커넥션 안에서 끝낸다.
        // TRUNCATE는 테이블마다 수십 ms라 빈 테이블이 많은 지금은 DELETE가 훨씬 빠르다
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    if (!KEEP.contains(table)) {
                        statement.execute("DELETE FROM `" + table + "`");
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

}
