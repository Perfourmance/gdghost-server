package com.gdghost.invite;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class InviteCodeRevokeIntegrationTest {

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
    void 폐기하면_revoked_at을_기록하고_운영_기록을_남긴다() throws Exception {
        long codeId = fixtures.inviteCode(5);

        revoke(codeId).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM invite_code WHERE id = ?",
                Boolean.class, codeId)).isTrue();
        Map<String, Object> audit = jdbc.queryForMap("SELECT actor_id, action, target_type, target_id FROM ops_audit_log");
        assertThat(audit).containsEntry("actor_id", staff.id())
                .containsEntry("action", "INVITE_CODE_REVOKE")
                .containsEntry("target_type", "INVITE_CODE")
                .containsEntry("target_id", codeId);
    }

    @Test
    void 이미_폐기된_코드도_204이고_기록은_한_번만() throws Exception {
        long codeId = fixtures.inviteCode(5);

        revoke(codeId).andExpect(status().isNoContent());
        revoke(codeId).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void 없는_코드면_404() throws Exception {
        revoke(999_999)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions revoke(long codeId) throws Exception {
        return mvc.perform(delete("/api/v1/ops/invite-codes/{codeId}", codeId).cookie(login).with(csrfToken()));
    }

}
