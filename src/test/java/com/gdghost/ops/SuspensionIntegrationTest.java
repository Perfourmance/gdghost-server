package com.gdghost.ops;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
class SuspensionIntegrationTest {

    private static final String REASON = "유출된 초대 코드로 가입한 외부인";

    // 로그인 IP 제한이 테스트끼리 섞이지 않게
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    String ip;
    TestMember staff;
    Cookie staffLogin;
    TestMember member;

    @BeforeEach
    void setUp() {
        ip = "10.29.0." + IP_SEQUENCE.incrementAndGet();
        staff = fixtures.staff();
        staffLogin = fixtures.loginCookie(staff);
        member = fixtures.member();
    }

    @Test
    void 정지하면_로그아웃되고_다시_로그인할_수_없다() throws Exception {
        Cookie memberLogin = fixtures.loginCookie(member);

        suspend(member.id(), REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.suspendedAt").exists())
                .andExpect(jsonPath("$.suspendedReason").value(REASON))
                .andExpect(jsonPath("$.suspendedBy.id").value(staff.id()))
                .andExpect(jsonPath("$.suspendedBy.nickname").value(staff.nickname()));

        mvc.perform(get("/api/v1/auth/me").cookie(memberLogin)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM login_token WHERE member_id = ?", Integer.class,
                member.id())).isZero();
        login(member)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM ops_audit_log");
        assertThat(audit).containsEntry("action", "SUSPEND").containsEntry("actor_id", staff.id())
                .containsEntry("target_member_id", member.id());
        assertThat(audit.get("detail").toString()).contains(REASON);
    }

    @Test
    void 이미_정지된_부원이면_그대로_돌려주고_기록은_한_번() throws Exception {
        suspend(member.id(), REASON);

        suspend(member.id(), "다른 사유")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedReason").value(REASON));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void 본인은_400() throws Exception {
        suspend(staff.id(), REASON)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 운영진이나_개발자는_409() throws Exception {
        suspend(fixtures.staff().id(), REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_IS_STAFF"));
        suspend(fixtures.dev().id(), REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TARGET_IS_STAFF"));
    }

    @Test
    void 탈퇴했거나_없는_부원은_404() throws Exception {
        suspend(fixtures.withdrawn().id(), REASON).andExpect(status().isNotFound());
        suspend(Long.MAX_VALUE, REASON).andExpect(status().isNotFound());
    }

    @Test
    void 사유는_2자_이상() throws Exception {
        suspend(member.id(), "a").andExpect(status().isBadRequest());
    }

    @Test
    void 해제하면_다시_로그인할_수_있다() throws Exception {
        suspend(member.id(), REASON);

        unsuspend(member.id()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap(
                "SELECT suspended_at, suspended_reason, suspended_by FROM member WHERE id = ?", member.id()))
                .containsEntry("suspended_at", null).containsEntry("suspended_reason", null)
                .containsEntry("suspended_by", null);
        login(member).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT action FROM ops_audit_log ORDER BY id", String.class))
                .containsExactly("SUSPEND", "UNSUSPEND");
    }

    @Test
    void 정지되지_않은_부원을_해제하면_404() throws Exception {
        unsuspend(member.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions suspend(long memberId, String reason) throws Exception {
        return mvc.perform(post("/api/v1/ops/members/{id}/suspension", memberId)
                .cookie(staffLogin)
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions unsuspend(long memberId) throws Exception {
        return mvc.perform(delete("/api/v1/ops/members/{id}/suspension", memberId)
                .cookie(staffLogin)
                .with(csrfToken()));
    }

    private ResultActions login(TestMember target) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + target.loginId() + "\",\"password\":\"" + Fixtures.PASSWORD + "\"}"));
    }

}
