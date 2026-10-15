package com.gdghost.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class AvailabilityIntegrationTest {

    // 요청 제한(IP당 분당 30회)이 테스트끼리 섞이지 않게
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    String ip;

    @BeforeEach
    void setUp() {
        ip = "10.20.1." + IP_SEQUENCE.incrementAndGet();
    }

    @Test
    void 아이디는_없으면_사용_가능하고_대소문자만_달라도_사용_중() throws Exception {
        TestMember member = fixtures.member();

        mvc.perform(loginId("newuser01")).andExpect(jsonPath("$.available").value(true));
        mvc.perform(loginId(member.loginId().toUpperCase())).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void 아이디_형식이_아니면_400() throws Exception {
        mvc.perform(loginId("ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 닉네임은_사용_중이면_TAKEN_익명으로_시작하면_RESERVED() throws Exception {
        TestMember member = fixtures.member();

        mvc.perform(nickname("새로운닉"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
        mvc.perform(nickname(member.nickname()))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("TAKEN"));
        mvc.perform(nickname("익명의부원"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("RESERVED"));
    }

    @Test
    void 로그인한_부원은_자기_닉네임이면_사용_가능() throws Exception {
        TestMember member = fixtures.member();

        mvc.perform(nickname(member.nickname()).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void 닉네임_길이가_어긋나면_400() throws Exception {
        mvc.perform(nickname("가"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private MockHttpServletRequestBuilder loginId(String value) {
        return fromIp(get("/api/v1/auth/login-ids/availability").param("loginId", value));
    }

    private MockHttpServletRequestBuilder nickname(String value) {
        return fromIp(get("/api/v1/auth/nicknames/availability").param("nickname", value));
    }

    private MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

}
