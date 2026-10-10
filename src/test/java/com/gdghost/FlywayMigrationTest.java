package com.gdghost;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gdghost.support.IntegrationTest;

@IntegrationTest
class FlywayMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void ERD의_테이블_38개가_만들어진다() {
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(tables).isEqualTo(38);
    }

    @Test
    void 태그_시드가_부록_A_B와_같다() {
        assertThat(countTags("INTEREST")).isEqualTo(9);
        assertThat(countTags("SKILL")).isEqualTo(56);
        assertThat(countTags("EXPERIENCE")).isEqualTo(18);
    }

    @Test
    void 두_분야에_걸친_세부_기술은_부모별로_따로_있다() {
        Integer flutter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tag WHERE kind = 'SKILL' AND name = 'Flutter'", Integer.class);

        assertThat(flutter).isEqualTo(2);
    }

    private Integer countTags(String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tag WHERE kind = ?", Integer.class, kind);
    }

}
