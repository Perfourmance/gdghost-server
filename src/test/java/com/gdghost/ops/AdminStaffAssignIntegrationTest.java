package com.gdghost.ops;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AdminStaffAssignIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember dev;
    Cookie login;

    @BeforeEach
    void setUp() {
        dev = fixtures.dev();
        login = fixtures.loginCookie(dev);
    }

    @Test
    void 부원을_운영진으로_지정하고_운영_기록을_남긴다() throws Exception {
        TestMember member = fixtures.member();

        assign(member.id(), "회장")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.staffTitle").value("회장"));

        assertThat(jdbc.queryForMap("SELECT role, staff_title FROM member WHERE id = ?", member.id()))
                .containsEntry("role", "STAFF")
                .containsEntry("staff_title", "회장");
        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT actor_id, action, target_member_id, detail->>'$.staffTitle' AS staff_title
                FROM ops_audit_log
                """);
        assertThat(audit).containsEntry("actor_id", dev.id())
                .containsEntry("action", "STAFF_ASSIGN")
                .containsEntry("target_member_id", member.id())
                .containsEntry("staff_title", "회장");
    }

    @Test
    void 이미_운영진이면_직함만_바꾼다() throws Exception {
        TestMember staff = fixtures.staff();

        assign(staff.id(), "부회장")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.staffTitle").value("부회장"));
    }

    @Test
    void 없는_부원이면_404() throws Exception {
        assign(999_999, "회장")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 정지된_부원은_400() throws Exception {
        assign(fixtures.suspended().id(), "회장")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 탈퇴한_부원은_400() throws Exception {
        assign(fixtures.withdrawn().id(), "회장")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 개발자_계정은_400() throws Exception {
        assign(fixtures.dev().id(), "회장")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isZero();
    }

    @Test
    void 직함이_2자보다_짧으면_400() throws Exception {
        assign(fixtures.member().id(), "회")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("staffTitle"));
    }

    private ResultActions assign(long memberId, String staffTitle) throws Exception {
        return mvc.perform(put("/api/v1/admin/members/{memberId}/staff", memberId).cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffTitle\":\"" + staffTitle + "\"}"));
    }

}
