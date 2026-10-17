package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class EmailChangeIntegrationTest {

    private static final String NEW_EMAIL = "new@gmail.com";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeMailGateway mail;

    TestMember member;
    Cookie login;
    String oldEmail;

    @BeforeEach
    void setUp() {
        mail.clear();
        member = fixtures.member();
        login = fixtures.loginCookie(member);
        oldEmail = member.loginId() + "@test.gdghost";
    }

    @Test
    void 새_메일로_인증번호를_보낸다() throws Exception {
        sendCode(Fixtures.PASSWORD, "New@Gmail.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.resendAvailableAt").exists());

        assertThat(mail.awaitSent(1).getFirst().to()).isEqualTo(NEW_EMAIL);
        assertThat(jdbc.queryForObject(
                "SELECT member_id FROM email_verification WHERE purpose = 'EMAIL_CHANGE' AND email = ?",
                Long.class, NEW_EMAIL)).isEqualTo(member.id());
    }

    @Test
    void 번호가_맞으면_바꾸고_다른_기기는_로그아웃_이전_메일로_안내한다() throws Exception {
        Cookie laptop = fixtures.loginCookie(member);
        sendCode(Fixtures.PASSWORD, NEW_EMAIL);

        String newToken = confirm(NEW_EMAIL, codeFromMail())
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(SessionCookie.NAME).getValue();

        assertThat(jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, member.id()))
                .isEqualTo(NEW_EMAIL);
        assertThat(jdbc.queryForObject("SELECT email_changed_at IS NOT NULL FROM member WHERE id = ?",
                Boolean.class, member.id())).isTrue();
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM email_change_log WHERE member_id = ?", member.id());
        assertThat(log).containsEntry("old_email", oldEmail).containsEntry("new_email", NEW_EMAIL)
                .containsEntry("changed_by_type", "SELF").containsEntry("changed_by", member.id());
        assertThat(log.get("verified_at")).isNotNull();

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie(SessionCookie.NAME, newToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
        assertThat(mail.awaitSent(2).getLast().to()).isEqualTo(oldEmail);
    }

    @Test
    void 현재_비밀번호가_틀리면_400_메일은_보내지_않는다() throws Exception {
        sendCode("wrong1234!", NEW_EMAIL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification", Integer.class)).isZero();
    }

    @Test
    void 현재_비밀번호_틀린_횟수는_비밀번호_변경과_합쳐_센다() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(put("/api/v1/me/password").cookie(login).with(csrfToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong1234!\",\"newPassword\":\"newpass123!\"}"))
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));
        }
        for (int i = 0; i < 2; i++) {
            sendCode("wrong1234!", NEW_EMAIL).andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));
        }

        sendCode(Fixtures.PASSWORD, NEW_EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void 지금_쓰는_메일이나_다른_부원의_메일이면_409() throws Exception {
        TestMember other = fixtures.member();

        sendCode(Fixtures.PASSWORD, oldEmail.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        sendCode(Fixtures.PASSWORD, other.loginId() + "@test.gdghost")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void 바꾼_지_30일이_안_됐으면_409() throws Exception {
        jdbc.update("UPDATE member SET email_changed_at = ? WHERE id = ?",
                Instant.now().minus(Duration.ofDays(29)), member.id());
        sendCode(Fixtures.PASSWORD, NEW_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_TOO_SOON"));

        jdbc.update("UPDATE member SET email_changed_at = ? WHERE id = ?",
                Instant.now().minus(Duration.ofDays(31)), member.id());
        sendCode(Fixtures.PASSWORD, NEW_EMAIL).andExpect(status().isAccepted());
    }

    @Test
    void 발송은_부원당_시간당_5회() throws Exception {
        for (int i = 1; i <= 5; i++) {
            sendCode(Fixtures.PASSWORD, "new" + i + "@gmail.com").andExpect(status().isAccepted());
        }

        sendCode(Fixtures.PASSWORD, "new6@gmail.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void 번호가_틀리면_400_틀린_횟수가_남는다() throws Exception {
        sendCode(Fixtures.PASSWORD, NEW_EMAIL);

        confirm(NEW_EMAIL, "000000".equals(codeFromMail()) ? "111111" : "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));

        assertThat(jdbc.queryForObject("SELECT attempt_count FROM email_verification WHERE email = ?",
                Integer.class, NEW_EMAIL)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, member.id()))
                .isEqualTo(oldEmail);
    }

    @Test
    void 다른_부원이_받은_번호로는_바꿀_수_없다() throws Exception {
        sendCode(Fixtures.PASSWORD, NEW_EMAIL);
        String code = codeFromMail();
        TestMember other = fixtures.member();

        mvc.perform(json("/api/v1/me/email-change/confirm", fixtures.loginCookie(other),
                        "{\"newEmail\":\"" + NEW_EMAIL + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    void 번호를_받은_뒤_다른_계정이_그_메일을_쓰면_409() throws Exception {
        sendCode(Fixtures.PASSWORD, NEW_EMAIL);
        String code = codeFromMail();
        TestMember other = fixtures.member();
        jdbc.update("UPDATE member SET email = ? WHERE id = ?", NEW_EMAIL, other.id());

        confirm(NEW_EMAIL, code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_change_log", Integer.class)).isZero();
    }

    private String codeFromMail() {
        Matcher code = Pattern.compile("\\d{6}").matcher(mail.awaitSent(1).getLast().text());
        assertThat(code.find()).isTrue();
        return code.group();
    }

    private ResultActions sendCode(String currentPassword, String newEmail) throws Exception {
        return mvc.perform(json("/api/v1/me/email-change/codes", login,
                "{\"currentPassword\":\"" + currentPassword + "\",\"newEmail\":\"" + newEmail + "\"}"));
    }

    private ResultActions confirm(String newEmail, String code) throws Exception {
        return mvc.perform(json("/api/v1/me/email-change/confirm", login,
                "{\"newEmail\":\"" + newEmail + "\",\"code\":\"" + code + "\"}"));
    }

    private MockHttpServletRequestBuilder json(String path, Cookie cookie, String content) {
        return post(path)
                .cookie(cookie)
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

}
