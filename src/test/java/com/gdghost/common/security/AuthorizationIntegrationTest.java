package com.gdghost.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

/**
 * 통과 쪽 검사. 실제 API가 아직 없어 시험용 컨트롤러를 붙인다
 * — 거부 쪽은 ApiPermissionTableTest.
 */
@IntegrationTest
@Import(AuthorizationIntegrationTest.ProbeController.class)
class AuthorizationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 로그인하면_AuthMember로_받는다() throws Exception {
        TestMember member = fixtures.member();

        mvc.perform(get("/api/v1/probe").cookie(fixtures.loginCookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void 운영진은_운영_API를_쓴다() throws Exception {
        mvc.perform(get("/api/v1/ops/probe").cookie(fixtures.loginCookie(fixtures.staff())))
                .andExpect(status().isOk());
    }

    @Test
    void 개발자는_어드민_API를_쓴다() throws Exception {
        mvc.perform(get("/api/v1/admin/probe").cookie(fixtures.loginCookie(fixtures.dev())))
                .andExpect(status().isOk());
    }

    @Test
    void 역할이_바뀌면_다음_요청부터_바로_반영된다() throws Exception {
        TestMember staff = fixtures.staff();
        var cookie = fixtures.loginCookie(staff);
        mvc.perform(get("/api/v1/ops/probe").cookie(cookie))
                .andExpect(status().isOk());

        jdbc.update("UPDATE member SET role = 'MEMBER' WHERE id = ?", staff.id());

        mvc.perform(get("/api/v1/ops/probe").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        AuthMember me(@AuthenticationPrincipal AuthMember me) {
            return me;
        }

        @GetMapping("/api/v1/ops/probe")
        void ops() {
        }

        @GetMapping("/api/v1/admin/probe")
        void admin() {
        }

    }

}
