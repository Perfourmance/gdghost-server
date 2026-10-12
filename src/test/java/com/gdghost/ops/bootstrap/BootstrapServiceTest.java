package com.gdghost.ops.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class BootstrapServiceTest {

    @Autowired
    BootstrapService bootstrap;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 초대_코드가_없으면_준비용_코드를_만든다() {
        bootstrap.apply(new BootstrapProperties("START2026", 5, List.of()));

        Map<String, Object> code = jdbc.queryForMap("""
                SELECT code, generation, created_by, max_uses, used_count,
                       TIMESTAMPDIFF(DAY, created_at, expires_at) AS days
                FROM invite_code
                """);
        assertThat(code).containsEntry("code", "START2026")
                .containsEntry("generation", 5)
                .containsEntry("created_by", null)
                .containsEntry("max_uses", 10)
                .containsEntry("used_count", 0)
                .containsEntry("days", 7L);
    }

    @Test
    void 초대_코드가_한_건이라도_있으면_만들지_않는다() {
        jdbc.update("""
                INSERT INTO invite_code (code, generation, max_uses, expires_at, revoked_at)
                VALUES ('OLD', 5, 10, ?, ?)
                """, LocalDateTime.now().plusDays(1), LocalDateTime.now());

        bootstrap.apply(new BootstrapProperties("START2026", 5, List.of()));

        assertThat(jdbc.queryForList("SELECT code FROM invite_code", String.class)).containsExactly("OLD");
    }

    @Test
    void 코드_환경변수가_비어_있으면_아무것도_하지_않는다() {
        bootstrap.apply(new BootstrapProperties(" ", null, List.of()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invite_code", Integer.class)).isZero();
    }

    @Test
    void 기수가_없으면_기동을_멈춘다() {
        assertThatThrownBy(() -> bootstrap.apply(new BootstrapProperties("START2026", null, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_GENERATION");
    }

    @Test
    void 개발자_아이디는_DEV로_만든다() {
        TestMember member = fixtures.member();
        TestMember staff = fixtures.staff();
        TestMember other = fixtures.member();

        bootstrap.apply(new BootstrapProperties(null, null,
                List.of(" " + member.loginId() + " ", staff.loginId(), "not-signed-up")));

        assertThat(role(member)).isEqualTo("DEV");
        assertThat(role(staff)).isEqualTo("DEV");
        assertThat(jdbc.queryForObject("SELECT staff_title FROM member WHERE id = ?", String.class, staff.id()))
                .isNull();
        assertThat(role(other)).isEqualTo("MEMBER");
    }

    @Test
    void 정지된_계정은_DEV로_만들지_않는다() {
        TestMember suspended = fixtures.suspended();

        bootstrap.apply(new BootstrapProperties(null, null, List.of(suspended.loginId())));

        assertThat(role(suspended)).isEqualTo("MEMBER");
    }

    private String role(TestMember member) {
        return jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, member.id());
    }

}
