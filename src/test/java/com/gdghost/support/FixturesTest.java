package com.gdghost.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.gdghost.support.Fixtures.TestMember;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class FixturesTest {

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 역할별_부원이_만들어진다() {
        assertThat(roleOf(fixtures.member())).isEqualTo("MEMBER");
        assertThat(roleOf(fixtures.staff())).isEqualTo("STAFF");
        assertThat(roleOf(fixtures.dev())).isEqualTo("DEV");
    }

    @Test
    void 비밀번호는_BCrypt로_저장된다() {
        TestMember member = fixtures.member();

        String hash = jdbc.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class, member.id());

        assertThat(new BCryptPasswordEncoder().matches(Fixtures.PASSWORD, hash)).isTrue();
    }

    @Test
    void 정지_부원은_정지_시각이_있다() {
        TestMember member = fixtures.suspended();

        assertThat(row(member).get("suspended_at")).isNotNull();
    }

    @Test
    void 탈퇴_부원은_유일_컬럼이_비워진다() {
        TestMember member = fixtures.withdrawn();

        Map<String, Object> row = row(member);
        assertThat(row.get("withdrawn_at")).isNotNull();
        assertThat(row.get("login_id")).isNull();
        assertThat(row.get("email")).isNull();
        assertThat(row.get("nickname")).isNull();
    }

    @Test
    void 로그인_쿠키는_DB에_해시로만_저장된다() {
        TestMember member = fixtures.member();

        Cookie cookie = fixtures.loginCookie(member);

        assertThat(cookie.getName()).isEqualTo("GDG_SESSION");
        Long owner = jdbc.queryForObject("SELECT member_id FROM login_token WHERE token_hash = ?",
                Long.class, Fixtures.sha256(cookie.getValue()));
        assertThat(owner).isEqualTo(member.id());
    }

    @Test
    void 테스트마다_DB가_비워진다() {
        Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class);

        assertThat(members).isZero();
    }

    private String roleOf(TestMember member) {
        return jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, member.id());
    }

    private Map<String, Object> row(TestMember member) {
        return jdbc.queryForMap("SELECT * FROM member WHERE id = ?", member.id());
    }

}
