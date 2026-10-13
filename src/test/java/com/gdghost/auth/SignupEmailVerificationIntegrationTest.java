package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.security.TokenHasher;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class SignupEmailVerificationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    FakeMailGateway mail;

    String token;

    @BeforeEach
    void setUp() {
        mail.clear();
        token = sessions.open(fixtures.inviteCode(5), 5).token();
    }

    @Test
    void 인증번호를_해시로_저장하고_메일로_보낸다() throws Exception {
        send("Hong@Gmail.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.resendAvailableAt").exists());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT email, member_id, purpose, code_hash, TIMESTAMPDIFF(MINUTE, created_at, expires_at) AS minutes
                FROM email_verification
                """);
        assertThat(row).containsEntry("email", "hong@gmail.com")
                .containsEntry("member_id", null)
                .containsEntry("purpose", "SIGNUP")
                .containsEntry("minutes", 10L);

        assertThat(awaitMail()).isTrue();
        assertThat(mail.sent().getFirst().to()).isEqualTo("hong@gmail.com");
        Matcher code = Pattern.compile("\\d{6}").matcher(mail.sent().getFirst().text());
        assertThat(code.find()).isTrue();
        assertThat(row.get("code_hash")).isEqualTo(TokenHasher.sha256(code.group()));
    }

    @Test
    void 가입_토큰이_없으면_400() throws Exception {
        token = "st_unknown";

        send("hong@gmail.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_SESSION_EXPIRED"));
    }

    @Test
    void 이미_가입된_메일이면_409() throws Exception {
        String email = fixtures.member().loginId() + "@test.gdghost";

        send(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void 육십초_안에_다시_보내면_429() throws Exception {
        send("hong@gmail.com").andExpect(status().isAccepted());

        send("hong@gmail.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_RESEND_TOO_SOON"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void 같은_메일_하루_5회를_넘으면_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            jdbc.update("""
                    INSERT INTO email_verification (email, code_hash, purpose, expires_at, created_at)
                    VALUES ('hong@gmail.com', 'x', 'SIGNUP', ?, ?)
                    """, Instant.now(), Instant.now().minus(Duration.ofMinutes(2)));
        }

        send("hong@gmail.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_DAILY_LIMIT"));
    }

    @Test
    void 메일_형식이_아니면_400() throws Exception {
        send("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions send(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup/email-verifications").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupToken\":\"" + token + "\",\"email\":\"" + email + "\"}"));
    }

    private boolean awaitMail() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (!mail.sent().isEmpty()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

}
