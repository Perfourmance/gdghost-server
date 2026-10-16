package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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

import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class AccountRecoveryIntegrationTest {

    // 요청 제한이 테스트끼리 섞이지 않게
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
        ip = "10.24.0." + IP_SEQUENCE.incrementAndGet();
        mail.clear();
        member = fixtures.member();
        email = member.loginId() + "@test.gdghost";
    }

    @Test
    void 가입된_메일이면_아이디를_보낸다() throws Exception {
        findLoginId(email.toUpperCase()).andExpect(status().isAccepted());

        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.sent().getFirst().to()).isEqualTo(email);
        assertThat(mail.sent().getFirst().text()).contains(member.loginId());
        assertThat(jdbc.queryForObject("SELECT member_id FROM email_verification WHERE purpose = 'FIND_LOGIN_ID'",
                Long.class)).isEqualTo(member.id());
    }

    @Test
    void 정지된_회원도_아이디를_받는다() throws Exception {
        TestMember suspended = fixtures.suspended();

        findLoginId(suspended.loginId() + "@test.gdghost").andExpect(status().isAccepted());

        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void 가입_안_된_메일도_같은_응답과_같은_제한_메일은_보내지_않는다() throws Exception {
        findLoginId("nobody@gmail.com").andExpect(status().isAccepted());
        assertThat(mail.sent()).isEmpty();

        findLoginId("nobody@gmail.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_RESEND_TOO_SOON"));
    }

    @Test
    void 재설정_인증번호와_재발송_대기를_같이_센다() throws Exception {
        sendResetCode(email).andExpect(status().isAccepted());

        findLoginId(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_RESEND_TOO_SOON"));
    }

    @Test
    void 재설정_인증번호와_하루_5회를_같이_센다() throws Exception {
        insertSent(email, "RESET", 3);
        insertSent(email, "FIND_LOGIN_ID", 2);

        findLoginId(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_DAILY_LIMIT"));
        sendResetCode(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_DAILY_LIMIT"));
    }

    @Test
    void 다른_용도의_인증번호는_세지_않는다() throws Exception {
        insertSent(email, "SIGNUP", 5);

        findLoginId(email).andExpect(status().isAccepted());
    }

    @Test
    void 아이디를_찾아도_받아_둔_재설정_번호는_그대로_쓴다() throws Exception {
        sendResetCode(email).andExpect(status().isAccepted());
        String code = codeFromMail();
        // 재발송 대기만 지난 것으로
        jdbc.update("UPDATE email_verification SET created_at = ?", Instant.now().minus(Duration.ofMinutes(2)));
        findLoginId(email).andExpect(status().isAccepted());

        mvc.perform(json("/api/v1/auth/password-reset/verify",
                "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
    }

    private void insertSent(String email, String purpose, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO email_verification (email, code_hash, purpose, expires_at, created_at)
                    VALUES (?, 'x', ?, ?, ?)
                    """, email, purpose, Instant.now(), Instant.now().minus(Duration.ofMinutes(2)));
        }
    }

    private String codeFromMail() {
        Matcher code = Pattern.compile("\\d{6}").matcher(mail.sent().getLast().text());
        assertThat(code.find()).isTrue();
        return code.group();
    }

    private ResultActions findLoginId(String email) throws Exception {
        return mvc.perform(json("/api/v1/auth/find-login-id", "{\"email\":\"" + email + "\"}"));
    }

    private ResultActions sendResetCode(String email) throws Exception {
        return mvc.perform(json("/api/v1/auth/password-reset/codes", "{\"email\":\"" + email + "\"}"));
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
