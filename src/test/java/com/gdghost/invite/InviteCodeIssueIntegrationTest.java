package com.gdghost.invite;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class InviteCodeIssueIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember staff;
    Cookie login;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        login = fixtures.loginCookie(staff);
    }

    @Test
    void 코드를_발급하고_운영_기록을_남긴다() throws Exception {
        issue("{\"generation\":5,\"expiresInDays\":14,\"maxUses\":60}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("[A-HJ-NP-Z2-9]{8}")))
                .andExpect(jsonPath("$.generation").value(5))
                .andExpect(jsonPath("$.maxUses").value(60))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.revokedAt").doesNotExist())
                .andExpect(jsonPath("$.createdBy.id").value(staff.id()))
                .andExpect(jsonPath("$.createdBy.nickname").value(staff.nickname()));

        assertThat(jdbc.queryForObject("""
                SELECT TIMESTAMPDIFF(DAY, created_at, expires_at) FROM invite_code
                """, Long.class)).isEqualTo(14);
        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT actor_id, action, target_type, detail->>'$.generation' AS generation FROM ops_audit_log
                """);
        assertThat(audit).containsEntry("actor_id", staff.id())
                .containsEntry("action", "INVITE_CODE_ISSUE")
                .containsEntry("target_type", "INVITE_CODE")
                .containsEntry("generation", "5");
    }

    @Test
    void 기한을_비우면_30일() throws Exception {
        issue("{\"generation\":5,\"maxUses\":60}").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("""
                SELECT TIMESTAMPDIFF(DAY, created_at, expires_at) FROM invite_code
                """, Long.class)).isEqualTo(30);
    }

    @Test
    void 같은_기수의_쓸_수_있는_코드는_자동_폐기한다() throws Exception {
        long sameGeneration = fixtures.inviteCode(5);
        long otherGeneration = fixtures.inviteCode(6);
        long expired = fixtures.inviteCode(5);
        jdbc.update("UPDATE invite_code SET expires_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1), expired);

        issue("{\"generation\":5,\"maxUses\":60}").andExpect(status().isCreated());

        assertThat(revoked(sameGeneration)).isTrue();
        assertThat(revoked(otherGeneration)).isFalse();
        assertThat(revoked(expired)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT JSON_LENGTH(detail->'$.revokedCodeIds') FROM ops_audit_log
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void 사용_횟수가_범위를_벗어나면_400() throws Exception {
        issue("{\"generation\":5,\"maxUses\":201}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("maxUses"));
    }

    @Test
    void 기수가_없으면_400() throws Exception {
        issue("{\"maxUses\":60}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("generation"));
    }

    private boolean revoked(long codeId) {
        return jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM invite_code WHERE id = ?",
                Boolean.class, codeId);
    }

    private ResultActions issue(String body) throws Exception {
        return mvc.perform(post("/api/v1/ops/invite-codes").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

}
