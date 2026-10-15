package com.gdghost.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * 로그인이 필요한 아무 API(내 세션 확인)로 필터 동작을 확인한다.
 */
@IntegrationTest
class CookieAuthIntegrationTest {

    private static final String ANY_API = "/api/v1/auth/me";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoginTokenService tokens;

    @Test
    void 쿠키가_없으면_401() throws Exception {
        mvc.perform(get(ANY_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(cookie().doesNotExist(SessionCookie.NAME));
    }

    @Test
    void 유효한_쿠키면_통과하고_24시간_전엔_연장하지_않는다() throws Exception {
        TestMember member = fixtures.member();
        Cookie cookie = fixtures.loginCookie(member);
        LocalDateTime before = expiresAt(cookie);

        mvc.perform(get(ANY_API).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(SessionCookie.NAME));

        assertThat(expiresAt(cookie)).isEqualTo(before);
    }

    @Test
    void 모르는_토큰이면_쿠키를_지우고_401() throws Exception {
        expectRejected(mvc.perform(get(ANY_API).cookie(new Cookie(SessionCookie.NAME, "unknown"))));
    }

    @Test
    void 만료된_토큰은_지우고_401() throws Exception {
        Cookie cookie = fixtures.loginCookie(fixtures.member());
        jdbc.update("UPDATE login_token SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND");

        expectRejected(mvc.perform(get(ANY_API).cookie(cookie)));
        assertThat(tokenCount()).isZero();
    }

    @Test
    void 정지된_계정의_토큰은_지우고_401() throws Exception {
        Cookie cookie = fixtures.loginCookie(fixtures.suspended());

        expectRejected(mvc.perform(get(ANY_API).cookie(cookie)));
        assertThat(tokenCount()).isZero();
    }

    @Test
    void 탈퇴한_계정의_토큰은_지우고_401() throws Exception {
        Cookie cookie = fixtures.loginCookie(fixtures.withdrawn());

        expectRejected(mvc.perform(get(ANY_API).cookie(cookie)));
        assertThat(tokenCount()).isZero();
    }

    @Test
    void 마지막_연장에서_24시간이_지나면_30일로_연장하고_쿠키를_다시_준다() throws Exception {
        Cookie cookie = fixtures.loginCookie(fixtures.member());
        jdbc.update("""
                UPDATE login_token SET last_used_at = UTC_TIMESTAMP(6) - INTERVAL 25 HOUR,
                                       expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 DAY
                """);

        mvc.perform(get(ANY_API).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(cookie().value(SessionCookie.NAME, cookie.getValue()))
                .andExpect(cookie().maxAge(SessionCookie.NAME, 2592000))
                .andExpect(cookie().path(SessionCookie.NAME, "/"))
                .andExpect(cookie().secure(SessionCookie.NAME, true))
                .andExpect(cookie().httpOnly(SessionCookie.NAME, true))
                .andExpect(cookie().attribute(SessionCookie.NAME, "SameSite", "Lax"));

        assertThat(expiresAt(cookie)).isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(29));
    }

    @Test
    void 로그인_유지를_끈_토큰은_연장해도_쿠키를_다시_주지_않는다() throws Exception {
        TestMember member = fixtures.member();
        String token = tokens.issue(member.id(), false, null);
        jdbc.update("UPDATE login_token SET last_used_at = UTC_TIMESTAMP(6) - INTERVAL 25 HOUR");

        mvc.perform(get(ANY_API).cookie(new Cookie(SessionCookie.NAME, token)))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(SessionCookie.NAME));
    }

    @Test
    void 발급하면_DB에는_해시만_남고_역할이_실린다() {
        TestMember staff = fixtures.staff();
        String token = tokens.issue(staff.id(), true, "Mozilla/5.0");

        assertThat(jdbc.queryForObject("SELECT token_hash FROM login_token", String.class))
                .isEqualTo(TokenHasher.sha256(token))
                .isNotEqualTo(token);
        assertThat(tokens.authenticate(token)).get()
                .extracting(LoginTokenService.Session::member)
                .isEqualTo(new AuthMember(staff.id(), Role.STAFF));
    }

    @Test
    void 폐기하면_더는_쓸_수_없다() {
        String token = tokens.issue(fixtures.member().id(), true, null);

        tokens.revoke(token);

        assertThat(tokens.authenticate(token)).isEmpty();
    }

    private void expectRejected(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(cookie().value(SessionCookie.NAME, ""))
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));
    }

    private LocalDateTime expiresAt(Cookie cookie) {
        return jdbc.queryForObject("SELECT expires_at FROM login_token WHERE token_hash = ?",
                LocalDateTime.class, TokenHasher.sha256(cookie.getValue()));
    }

    private int tokenCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM login_token", Integer.class);
    }

}
