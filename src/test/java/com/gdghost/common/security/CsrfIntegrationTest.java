package com.gdghost.common.security;

import static com.gdghost.support.TestCsrf.COOKIE;
import static com.gdghost.support.TestCsrf.HEADER;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/**
 * 프론트(axios)처럼 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 다시 보내는 흐름을 그대로 검사한다.
 */
@IntegrationTest
class CsrfIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Test
    void 비회원의_auth_me_401_응답에도_토큰_쿠키를_준다() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists(COOKIE))
                .andExpect(cookie().path(COOKIE, "/"))
                .andExpect(cookie().secure(COOKIE, true))
                .andExpect(cookie().httpOnly(COOKIE, false))
                .andExpect(cookie().attribute(COOKIE, "SameSite", "Lax"));
    }

    @Test
    void 이미_쿠키가_있으면_다시_주지_않는다() throws Exception {
        Cookie csrf = issueCsrfCookie();

        mvc.perform(get("/api/v1/auth/me").cookie(csrf))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, not(hasItem(containsString(COOKIE)))));
    }

    @Test
    void 토큰_없는_쓰기_요청은_403_CSRF_INVALID() throws Exception {
        mvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void 헤더가_쿠키와_다르면_403_CSRF_INVALID() throws Exception {
        mvc.perform(post("/api/v1/auth/login").cookie(issueCsrfCookie()).header(HEADER, "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void 로그인했어도_토큰이_없으면_403() throws Exception {
        mvc.perform(post("/api/v1/questions").cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void 쿠키_값을_헤더로_보내면_통과한다() throws Exception {
        Cookie csrf = issueCsrfCookie();

        // 컨트롤러가 아직 없어 CSRF를 통과하면 404
        mvc.perform(post("/api/v1/questions")
                        .cookie(csrf, fixtures.loginCookie(fixtures.member()))
                        .header(HEADER, csrf.getValue()))
                .andExpect(status().isNotFound());
    }

    private Cookie issueCsrfCookie() throws Exception {
        return mvc.perform(get("/api/v1/auth/me")).andReturn().getResponse().getCookie(COOKIE);
    }

}
