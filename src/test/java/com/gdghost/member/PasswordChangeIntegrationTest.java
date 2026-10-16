package com.gdghost.member;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.common.security.TokenHasher;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class PasswordChangeIntegrationTest {

    private static final String NEW_PASSWORD = "newpass123!";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    TestMember member;
    Cookie login;

    @BeforeEach
    void setUp() {
        member = fixtures.member();
        login = fixtures.loginCookie(member);
    }

    @Test
    void 바꾸면_지금_기기는_새_쿠키로_유지되고_다른_기기는_로그아웃된다() throws Exception {
        Cookie laptop = fixtures.loginCookie(member);

        String newToken = change(Fixtures.PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 30 * 24 * 60 * 60))
                .andReturn().getResponse().getCookie(SessionCookie.NAME).getValue();

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie(SessionCookie.NAME, newToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").cookie(login)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_token WHERE member_id = ?", Integer.class,
                member.id())).isEqualTo(1);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT password_changed_at IS NOT NULL FROM member WHERE id = ?",
                Boolean.class, member.id())).isTrue();
    }

    @Test
    void 로그인_유지를_안_한_기기는_새_쿠키도_브라우저를_닫으면_끝난다() throws Exception {
        jdbc.update("UPDATE login_token SET remember = FALSE WHERE token_hash = ?",
                TokenHasher.sha256(login.getValue()));

        change(Fixtures.PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookie.NAME, -1));
        assertThat(jdbc.queryForObject("SELECT remember FROM login_token WHERE member_id = ?", Boolean.class,
                member.id())).isFalse();
    }

    @Test
    void 현재_비밀번호가_틀리면_400_아무것도_바뀌지_않는다() throws Exception {
        change("wrong1234!", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));

        assertThat(passwordEncoder.matches(Fixtures.PASSWORD, passwordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(login)).andExpect(status().isOk());
    }

    @Test
    void 새_비밀번호가_규칙에_어긋나면_400() throws Exception {
        change(Fixtures.PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));

        assertThat(passwordEncoder.matches(Fixtures.PASSWORD, passwordHash())).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(login)).andExpect(status().isOk());
    }

    @Test
    void 현재_비밀번호를_5회_틀리면_맞아도_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            change("wrong1234!", NEW_PASSWORD).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));
        }

        change(Fixtures.PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void 성공하면_틀린_횟수가_초기화된다() throws Exception {
        for (int i = 0; i < 4; i++) {
            change("wrong1234!", NEW_PASSWORD);
        }
        String newToken = change(Fixtures.PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(SessionCookie.NAME).getValue();
        login = new Cookie(SessionCookie.NAME, newToken);

        change("wrong1234!", "another123!").andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));
        change(NEW_PASSWORD, "another123!").andExpect(status().isNoContent());
    }

    private String passwordHash() {
        return jdbc.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class, member.id());
    }

    private ResultActions change(String currentPassword, String newPassword) throws Exception {
        return mvc.perform(put("/api/v1/me/password")
                .cookie(login)
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + newPassword
                        + "\"}"));
    }

}
