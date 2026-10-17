    package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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

import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class StaffEmailChangeIntegrationTest {

    private static final String NEW_EMAIL = "new@gmail.com";
    private static final String NOTE = "본인 디스코드 DM으로 요청";

    // 링크 확인의 IP 제한이 테스트끼리 섞이지 않게
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
    TestMember dev;
    Cookie devLogin;
    TestMember member;
    String oldEmail;

    @BeforeEach
    void setUp() {
        ip = "10.27.0." + IP_SEQUENCE.incrementAndGet();
        mail.clear();
        dev = fixtures.dev();
        devLogin = fixtures.loginCookie(dev);
        member = fixtures.member();
        oldEmail = member.loginId() + "@test.gdghost";
    }

    @Test
    void 요청하면_새_메일로_링크_이전_메일로_알림을_보내고_기록을_남긴다() throws Exception {
        request(member.id(), "New@Gmail.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(mail.awaitSent(2)).extracting(sent -> sent.to()).containsExactlyInAnyOrder(NEW_EMAIL, oldEmail);
        assertThat(jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, member.id()))
                .isEqualTo(oldEmail);
        Map<String, Object> log = jdbc.queryForMap("SELECT * FROM email_change_log WHERE member_id = ?", member.id());
        assertThat(log).containsEntry("old_email", oldEmail).containsEntry("new_email", NEW_EMAIL)
                .containsEntry("changed_by_type", "STAFF").containsEntry("changed_by", dev.id())
                .containsEntry("verified_at", null);
        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM ops_audit_log WHERE action = 'EMAIL_CHANGE_REQUEST'");
        assertThat(audit).containsEntry("actor_id", dev.id()).containsEntry("target_member_id", member.id());
        assertThat(audit.get("detail").toString()).contains(NOTE).doesNotContain(NEW_EMAIL);
    }

    @Test
    void 링크를_열면_바꾸고_모든_기기를_로그아웃_이전_메일로_안내한다() throws Exception {
        Cookie phone = fixtures.loginCookie(member);
        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class,
                member.id());
        request(member.id(), NEW_EMAIL);

        confirm(tokenFromMail()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, member.id()))
                .isEqualTo(NEW_EMAIL);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class, member.id()))
                .isEqualTo(passwordHash);
        assertThat(jdbc.queryForObject("SELECT verified_at IS NOT NULL FROM email_change_log WHERE member_id = ?",
                Boolean.class, member.id())).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
        assertThat(mail.awaitSent(3)).filteredOn(sent -> sent.subject().contains("변경 안내"))
                .extracting(sent -> sent.to()).containsExactly(oldEmail);
    }

    @Test
    void 링크는_한_번만_쓴다() throws Exception {
        request(member.id(), NEW_EMAIL);
        String token = tokenFromMail();
        confirm(token).andExpect(status().isNoContent());

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 하루가_지난_링크는_400() throws Exception {
        request(member.id(), NEW_EMAIL);
        jdbc.update("UPDATE email_verification SET expires_at = ?", Instant.now().minusSeconds(1));

        confirm(tokenFromMail())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 다시_요청하면_이전_링크는_무효() throws Exception {
        request(member.id(), NEW_EMAIL);
        String first = tokenFromMail();
        request(member.id(), "other@gmail.com").andExpect(status().isAccepted());

        confirm(first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 본인_변경_인증번호로는_링크_확인을_할_수_없다() throws Exception {
        mvc.perform(post("/api/v1/me/email-change/codes").cookie(fixtures.loginCookie(member)).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + Fixtures.PASSWORD + "\",\"newEmail\":\"" + NEW_EMAIL
                                + "\"}"))
                .andExpect(status().isAccepted());
        Matcher code = Pattern.compile("\\d{6}").matcher(mail.awaitSent(1).getFirst().text());
        assertThat(code.find()).isTrue();

        confirm(code.group())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void 개발자_본인_비밀번호가_틀리면_400_메일은_보내지_않는다() throws Exception {
        request(member.id(), NEW_EMAIL, "wrong1234!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_WRONG"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isZero();
    }

    @Test
    void 개발자_본인_비밀번호를_5번_틀리면_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            request(member.id(), NEW_EMAIL, "wrong1234!");
        }

        request(member.id(), NEW_EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void 개발자_자기_자신은_403() throws Exception {
        request(dev.id(), NEW_EMAIL)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 탈퇴했거나_없는_부원은_404() throws Exception {
        request(fixtures.withdrawn().id(), NEW_EMAIL).andExpect(status().isNotFound());
        request(Long.MAX_VALUE, NEW_EMAIL).andExpect(status().isNotFound());
    }

    @Test
    void 정지된_부원도_요청할_수_있다() throws Exception {
        request(fixtures.suspended().id(), NEW_EMAIL).andExpect(status().isAccepted());
    }

    @Test
    void 지금_쓰는_메일이나_다른_부원의_메일이면_409() throws Exception {
        request(member.id(), oldEmail)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        request(member.id(), dev.loginId() + "@test.gdghost")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void 바꾼_지_30일이_안_됐으면_409() throws Exception {
        jdbc.update("UPDATE member SET email_changed_at = ? WHERE id = ?",
                Instant.now().minus(Duration.ofDays(29)), member.id());

        request(member.id(), NEW_EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_TOO_SOON"));
    }

    @Test
    void 링크를_받은_뒤_다른_계정이_그_메일을_쓰면_409_링크는_남는다() throws Exception {
        request(member.id(), NEW_EMAIL);
        String token = tokenFromMail();
        TestMember other = fixtures.member();
        jdbc.update("UPDATE member SET email = ? WHERE id = ?", NEW_EMAIL, other.id());

        confirm(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
        assertThat(jdbc.queryForObject("SELECT verified_at IS NULL FROM email_verification", Boolean.class))
                .isTrue();
    }

    /** 이전 메일 알림과 순서가 섞이므로 가장 최근 링크 메일을 찾는다 */
    private String tokenFromMail() {
        mail.awaitSent(2);
        Matcher token = Pattern.compile("token=([\\w-]+)").matcher(mail.sent().reversed().stream()
                .filter(sent -> sent.text().contains("token="))
                .findFirst().orElseThrow().text());
        assertThat(token.find()).isTrue();
        return token.group(1);
    }

    private ResultActions request(long memberId, String newEmail) throws Exception {
        return request(memberId, newEmail, Fixtures.PASSWORD);
    }

    private ResultActions request(long memberId, String newEmail, String currentPassword) throws Exception {
        return mvc.perform(post("/api/v1/admin/members/{memberId}/email-change", memberId)
                .cookie(devLogin)
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + currentPassword + "\",\"newEmail\":\"" + newEmail
                        + "\",\"identityNote\":\"" + NOTE + "\"}"));
    }

    private ResultActions confirm(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/email-change/confirm")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

}
