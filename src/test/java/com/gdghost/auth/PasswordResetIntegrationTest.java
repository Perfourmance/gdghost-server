package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.gdghost.common.security.TokenHasher;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class PasswordResetIntegrationTest {

    private static final String NEW_PASSWORD = "newpass123!";
    // 요청 제한 · 로그인 실패 횟수가 테스트끼리 섞이지 않게
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeMailGateway mail;

    String ip;
    TestMember member;
    String email;

    @BeforeEach
    void setUp() {
        ip = "10.23.0." + IP_SEQUENCE.incrementAndGet();
        mail.clear();
        member = fixtures.member();
        email = member.loginId() + "@test.gdghost";
    }

    @Test
    void 가입된_메일이면_인증번호를_보낸다() throws Exception {
        sendCode(email.toUpperCase())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resendAvailableAt").exists());

        assertThat(mail.awaitSent(1)).hasSize(1);
        assertThat(mail.sent().getFirst().to()).isEqualTo(email);
        assertThat(jdbc.queryForObject("SELECT member_id FROM email_verification WHERE purpose = 'RESET'",
                Long.class)).isEqualTo(member.id());
    }

    @Test
    void 가입_안_된_메일도_같은_응답과_같은_제한_메일은_보내지_않는다() throws Exception {
        sendCode("nobody@gmail.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resendAvailableAt").exists());
        assertThat(mail.sent()).isEmpty();

        sendCode("nobody@gmail.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_RESEND_TOO_SOON"));
    }

    @Test
    void 인증번호를_확인하고_새_비밀번호를_설정하면_모든_기기가_로그아웃된다() throws Exception {
        Cookie phone = fixtures.loginCookie(member);
        Cookie laptop = fixtures.loginCookie(member);
        sendCode(email);

        String resetToken = resetToken(codeFromMail());
        assertThat(resetToken).startsWith("rt_");
        reset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT password_changed_at IS NOT NULL FROM member WHERE id = ?",
                Boolean.class, member.id())).isTrue();
        login(Fixtures.PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void 재설정_토큰은_한_번만_쓴다() throws Exception {
        sendCode(email);
        String resetToken = resetToken(codeFromMail());
        reset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());

        reset(resetToken, "another123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 비밀번호_규칙에_걸리면_같은_토큰으로_다시_시도할_수_있다() throws Exception {
        sendCode(email);
        String resetToken = resetToken(codeFromMail());

        reset(resetToken, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY"));
        reset(resetToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void 없는_토큰이면_400() throws Exception {
        reset("rt_unknown", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 번호가_틀리면_400() throws Exception {
        sendCode(email);

        verify(email, "000000".equals(codeFromMail()) ? "111111" : "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    void 가입_안_된_메일은_번호가_맞아도_INVALID() throws Exception {
        jdbc.update("""
                INSERT INTO email_verification (email, code_hash, purpose, expires_at, created_at)
                VALUES ('nobody@gmail.com', ?, 'RESET', ?, ?)
                """, TokenHasher.sha256("482913"), Instant.now().plus(10, ChronoUnit.MINUTES), Instant.now());

        verify("nobody@gmail.com", "482913")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    void 로그인이_잠긴_아이디도_재설정하면_바로_로그인된다() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("wrong1234!");
        }
        login(Fixtures.PASSWORD).andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

        sendCode(email);
        reset(resetToken(codeFromMail()), NEW_PASSWORD).andExpect(status().isNoContent());

        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    private String codeFromMail() {
        Matcher code = Pattern.compile("\\d{6}").matcher(mail.awaitSent(1).getLast().text());
        assertThat(code.find()).isTrue();
        return code.group();
    }

    private String resetToken(String code) throws Exception {
        String body = verify(email, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.resetToken");
    }

    private ResultActions sendCode(String email) throws Exception {
        return mvc.perform(json("/api/v1/auth/password-reset/codes", "{\"email\":\"" + email + "\"}"));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mvc.perform(json("/api/v1/auth/password-reset/verify",
                "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions reset(String resetToken, String newPassword) throws Exception {
        return mvc.perform(json("/api/v1/auth/password-reset",
                "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(json("/api/v1/auth/login",
                "{\"loginId\":\"" + member.loginId() + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpServletRequestBuilder json(String path, String content) {
        return post(path)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

}
