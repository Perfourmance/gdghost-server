package com.gdghost.ops;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class OpsStaffIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeDiscordGateway discord;

    @Autowired
    FakePushGateway push;

    TestMember staff;
    Cookie login;

    @BeforeEach
    void setUp() {
        discord.clear();
        push.clear();
        staff = fixtures.staff();
        login = fixtures.loginCookie(staff);
    }

    @Test
    void 지정하면_기록하고_대상에게_알리고_디스코드에_남긴다() throws Exception {
        TestMember member = fixtures.member();

        assign(member.id(), "총무")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.staffTitle").value("총무"));

        assertThat(jdbc.queryForMap("SELECT actor_id, action, target_member_id FROM ops_audit_log"))
                .containsEntry("actor_id", staff.id())
                .containsEntry("action", "STAFF_ASSIGN")
                .containsEntry("target_member_id", member.id());
        assertThat(notificationTitles(member)).containsExactly("운영진으로 지정됐어요");
        discord.await(s -> s.is(DiscordChannel.STAFF, "운영진 지정")
                && s.message().description().equals(staff.nickname() + " → " + member.nickname() + " (총무)"));
    }

    @Test
    void 직함을_바꾸면_직함_변경으로_알리고_같은_직함이면_아무것도_남기지_않는다() throws Exception {
        TestMember other = fixtures.staff();

        assign(other.id(), "부회장").andExpect(status().isOk());
        assign(other.id(), "부회장")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffTitle").value("부회장"));

        assertThat(notificationTitles(other)).containsExactly("운영진 직함이 바뀌었어요");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void 본인_직함을_바꾸면_본인에게_알리지_않는다() throws Exception {
        assign(staff.id(), "회장").andExpect(status().isOk());

        assertThat(notificationTitles(staff)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void 지정할_수_없는_계정은_400이고_없는_부원은_404() throws Exception {
        for (TestMember target : List.of(fixtures.suspended(), fixtures.withdrawn(), fixtures.dev())) {
            assign(target.id(), "회장")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assign(Long.MAX_VALUE, "회장")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assign(fixtures.member().id(), "회")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("staffTitle"));
    }

    @Test
    void 해제하면_부원이_되고_기록하고_앱_내로만_알린다() throws Exception {
        TestMember other = fixtures.staff();
        assign(other.id(), "총무").andExpect(status().isOk());
        jdbc.update("DELETE FROM ops_audit_log");
        jdbc.update("DELETE FROM notification");
        String endpoint = "https://push.test/" + other.id();
        jdbc.update("""
                INSERT INTO push_subscription (member_id, endpoint, endpoint_hash, p256dh, auth)
                VALUES (?, ?, SHA2(endpoint, 256), 'key', 'auth')
                """, other.id(), endpoint);
        push.clear();

        revoke(other.id()).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("SELECT role, staff_title FROM member WHERE id = ?", other.id());
        assertThat(row.get("role")).isEqualTo("MEMBER");
        assertThat(row.get("staff_title")).isNull();
        assertThat(jdbc.queryForMap("""
                SELECT actor_id, action, target_member_id, detail->>'$.staffTitle' AS staff_title
                FROM ops_audit_log
                """))
                .containsEntry("actor_id", staff.id())
                .containsEntry("action", "STAFF_REVOKE")
                .containsEntry("target_member_id", other.id())
                .containsEntry("staff_title", "총무");
        assertThat(notificationTitles(other)).containsExactly("운영진에서 해제됐어요");
        discord.await(s -> s.is(DiscordChannel.STAFF, "운영진 해제")
                && s.message().description().equals(staff.nickname() + " → " + other.nickname() + " (총무)"));
        assertThat(push.after(Duration.ofMillis(200), s -> s.target().endpoint().equals(endpoint))).isEmpty();
    }

    @Test
    void 본인도_해제할_수_있고_다음_요청부터_운영_API가_403() throws Exception {
        fixtures.staff();
        mvc.perform(get("/api/v1/ops/members").cookie(login)).andExpect(status().isOk());

        revoke(staff.id()).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/ops/members").cookie(login))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/auth/me").cookie(login)).andExpect(status().isOk());
        assertThat(notificationTitles(staff)).isEmpty();
    }

    @Test
    void 마지막_운영진은_해제할_수_없다_탈퇴한_운영진은_세지_않는다() throws Exception {
        TestMember withdrawn = fixtures.withdrawn();
        jdbc.update("UPDATE member SET role = 'STAFF' WHERE id = ?", withdrawn.id());

        revoke(staff.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_STAFF"));

        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE id = ?", String.class, staff.id()))
                .isEqualTo("STAFF");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isZero();
    }

    @Test
    void 운영진이_아니면_마지막_운영진이어도_404가_먼저다() throws Exception {
        TestMember withdrawn = fixtures.withdrawn();
        jdbc.update("UPDATE member SET role = 'STAFF' WHERE id = ?", withdrawn.id());

        for (long target : List.of(fixtures.member().id(), fixtures.dev().id(), withdrawn.id(), Long.MAX_VALUE)) {
            revoke(target)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void 해제된_운영진은_일반_부원처럼_정지할_수_있다() throws Exception {
        TestMember other = fixtures.staff();
        revoke(other.id()).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/ops/members/{id}/suspension", other.id()).cookie(login).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"규칙 위반\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 둘이_동시에_본인을_해제해도_한_명은_남는다() throws Exception {
        TestMember other = fixtures.staff();
        Cookie otherLogin = fixtures.loginCookie(other);
        List<Callable<Integer>> tasks = List.of(
                () -> revokeAs(staff.id(), login),
                () -> revokeAs(other.id(), otherLogin));

        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        List<Integer> statuses = new ArrayList<>();
        try {
            for (Future<Integer> result : pool.invokeAll(tasks)) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE role = 'STAFF'", Integer.class))
                .isEqualTo(1);
    }

    private int revokeAs(long memberId, Cookie cookie) throws Exception {
        return mvc.perform(delete("/api/v1/ops/members/{id}/staff", memberId).cookie(cookie).with(csrfToken()))
                .andReturn().getResponse().getStatus();
    }

    private List<String> notificationTitles(TestMember member) {
        return jdbc.queryForList("SELECT title FROM notification WHERE member_id = ? AND type = 'STAFF_ROLE_CHANGED'",
                String.class, member.id());
    }

    private ResultActions assign(long memberId, String staffTitle) throws Exception {
        return mvc.perform(put("/api/v1/ops/members/{id}/staff", memberId).cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffTitle\":\"" + staffTitle + "\"}"));
    }

    private ResultActions revoke(long memberId) throws Exception {
        return mvc.perform(delete("/api/v1/ops/members/{id}/staff", memberId).cookie(login).with(csrfToken()));
    }

}
