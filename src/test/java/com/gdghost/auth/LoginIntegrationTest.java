package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class LoginIntegrationTest {

    // IP당 실패 횟수가 테스트끼리 섞이지 않게
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    String ip;
    TestMember member;

    @BeforeEach
    void setUp() {
        ip = "10.21.0." + IP_SEQUENCE.incrementAndGet();
        member = fixtures.member();
    }

    @Test
    void 로그인하면_30일_쿠키와_Me를_준다() throws Exception {
        Cookie login = login(member.loginId(), Fixtures.PASSWORD, null)
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 30 * 24 * 60 * 60))
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.loginId").value(member.loginId()))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.profileCompleteness.total").value(6))
                .andReturn().getResponse().getCookie(SessionCookie.NAME);

        assertThat(jdbc.queryForObject("SELECT last_seen_at IS NOT NULL FROM member WHERE id = ?", Boolean.class,
                member.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT remember FROM login_token WHERE member_id = ?", Boolean.class,
                member.id())).isTrue();
        // 받은 쿠키로 로그인 상태 — 자기 닉네임은 사용 가능으로 나온다
        mvc.perform(get("/api/v1/auth/nicknames/availability").param("nickname", member.nickname()).cookie(login))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 로그인_유지를_끄면_브라우저를_닫으면_끝나는_쿠키() throws Exception {
        login(member.loginId(), Fixtures.PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(SessionCookie.NAME, -1));

        assertThat(jdbc.queryForObject("SELECT remember FROM login_token WHERE member_id = ?", Boolean.class,
                member.id())).isFalse();
    }

    @Test
    void 아이디는_대소문자를_가리지_않는다() throws Exception {
        login(member.loginId().toUpperCase(), Fixtures.PASSWORD, null).andExpect(status().isOk());
    }

    @Test
    void 이미_로그인한_기기에서_다시_로그인하면_예전_토큰을_지운다() throws Exception {
        Cookie previous = fixtures.loginCookie(member);

        mvc.perform(request(member.loginId(), Fixtures.PASSWORD, null).cookie(previous))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_token WHERE member_id = ?", Integer.class,
                member.id())).isEqualTo(1);
        // 예전 쿠키는 비회원으로 처리돼 자기 닉네임도 사용 중으로 나온다
        mvc.perform(get("/api/v1/auth/nicknames/availability").param("nickname", member.nickname()).cookie(previous))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 비밀번호가_틀려도_없는_아이디여도_같은_401() throws Exception {
        login(member.loginId(), "wrong1234!", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login("nobody99", Fixtures.PASSWORD, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 같은_아이디로_5회_틀리면_맞는_비밀번호도_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(member.loginId(), "wrong1234!", null).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }

        login(member.loginId(), Fixtures.PASSWORD, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void 성공하면_아이디_실패_횟수를_비운다() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(member.loginId(), "wrong1234!", null);
        }
        login(member.loginId(), Fixtures.PASSWORD, null).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login(member.loginId(), "wrong1234!", null).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }
        login(member.loginId(), Fixtures.PASSWORD, null).andExpect(status().isOk());
    }

    @Test
    void 같은_IP에서_아이디를_바꿔_50회_틀리면_429() throws Exception {
        for (int i = 0; i < 50; i++) {
            login("nobody" + i, "wrong1234!", null).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }

        login(member.loginId(), Fixtures.PASSWORD, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void 정지된_계정은_비밀번호가_맞을_때만_403() throws Exception {
        TestMember suspended = fixtures.suspended();

        login(suspended.loginId(), "wrong1234!", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login(suspended.loginId(), Fixtures.PASSWORD, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(cookie().doesNotExist(SessionCookie.NAME));
    }

    @Test
    void 아이디가_비면_400() throws Exception {
        login(" ", Fixtures.PASSWORD, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions login(String loginId, String password, Boolean remember) throws Exception {
        return mvc.perform(request(loginId, password, remember));
    }

    private MockHttpServletRequestBuilder request(String loginId, String password, Boolean remember) {
        String rememberField = remember == null ? "" : ",\"remember\":" + remember;
        return post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"" + rememberField + "}");
    }

}
