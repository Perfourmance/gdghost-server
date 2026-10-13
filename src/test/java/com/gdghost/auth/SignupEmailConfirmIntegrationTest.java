package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.security.TokenHasher;
import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class SignupEmailConfirmIntegrationTest {

    private static final String EMAIL = "hong@gmail.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SignupSessionStore sessions;

    String token;

    @BeforeEach
    void setUp() {
        token = sessions.open(fixtures.inviteCode(5), 5).token();
    }

    @Test
    void 맞으면_가입_토큰에_인증된_메일을_기록한다() throws Exception {
        long id = sent("482913", Instant.now().plus(Duration.ofMinutes(10)));

        confirm("Hong@Gmail.com", "482913")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        assertThat(jdbc.queryForObject("SELECT verified_at FROM email_verification WHERE id = ?", LocalDateTime.class, id))
                .isNotNull();
        assertThat(sessions.get(token).verifiedEmail()).isEqualTo(EMAIL);
    }

    @Test
    void 틀리면_남은_횟수를_알려준다() throws Exception {
        long id = sent("482913", Instant.now().plus(Duration.ofMinutes(10)));

        confirm(EMAIL, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"))
                .andExpect(jsonPath("$.detail").value("인증번호가 맞지 않습니다. 남은 횟수 4회"));

        assertThat(jdbc.queryForObject("SELECT attempt_count FROM email_verification WHERE id = ?", Integer.class,
                id)).isEqualTo(1);
        assertThat(sessions.get(token).verifiedEmail()).isNull();
    }

    @Test
    void 다섯_번_틀리면_맞는_번호도_만료() throws Exception {
        sent("482913", Instant.now().plus(Duration.ofMinutes(10)));
        for (int i = 0; i < 4; i++) {
            confirm(EMAIL, "000000").andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
        }

        confirm(EMAIL, "000000").andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
        confirm(EMAIL, "482913")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    void 십분이_지나면_만료() throws Exception {
        sent("482913", Instant.now().minusSeconds(1));

        confirm(EMAIL, "482913").andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    void 다시_받으면_이전_번호는_무효() throws Exception {
        sent("111111", Instant.now().plus(Duration.ofMinutes(10)));
        sent("222222", Instant.now().plus(Duration.ofMinutes(10)));

        confirm(EMAIL, "111111").andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
        confirm(EMAIL, "222222").andExpect(status().isOk());
    }

    @Test
    void 받은_적_없는_메일이면_400() throws Exception {
        confirm(EMAIL, "482913")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    void 가입_토큰이_없으면_400() throws Exception {
        sent("482913", Instant.now().plus(Duration.ofMinutes(10)));
        token = "st_unknown";

        confirm(EMAIL, "482913").andExpect(jsonPath("$.code").value("SIGNUP_SESSION_EXPIRED"));
    }

    @Test
    void 번호가_숫자_6자리가_아니면_400() throws Exception {
        confirm(EMAIL, "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private long sent(String code, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO email_verification (email, code_hash, purpose, expires_at, created_at)
                VALUES (?, ?, 'SIGNUP', ?, ?)
                """, EMAIL, TokenHasher.sha256(code), expiresAt, Instant.now());
        return jdbc.queryForObject("SELECT MAX(id) FROM email_verification", Long.class);
    }

    private ResultActions confirm(String email, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup/email-verifications/confirm").with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signupToken\":\"" + token + "\",\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

}
