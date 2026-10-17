package com.gdghost.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class OpsMemberIntegrationTest {

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
        jdbc.update("UPDATE member SET staff_title = '회장' WHERE id = ?", staff.id());
        login = fixtures.loginCookie(staff);
    }

    @Test
    void 목록은_최근_가입순_숨김_포함_탈퇴_제외_이메일은_없다() throws Exception {
        TestMember hidden = fixtures.member();
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());
        TestMember withdrawn = fixtures.withdrawn();
        TestMember latest = fixtures.member();

        list("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].id").value(contains(
                        (int) latest.id(), (int) hidden.id(), (int) staff.id())))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem((int) withdrawn.id()))))
                .andExpect(jsonPath("$.items[0].loginId").value(latest.loginId()))
                .andExpect(jsonPath("$.items[0].joinedAt").exists())
                .andExpect(jsonPath("$.items[1].hiddenFromList").value(true))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());
    }

    @Test
    void 이름_닉네임_아이디로_찾는다() throws Exception {
        TestMember member = fixtures.member();

        list("?q=" + member.loginId()).andExpect(jsonPath("$.items[*].id").value(contains((int) member.id())));
        list("?q=" + member.nickname()).andExpect(jsonPath("$.items[*].id").value(contains((int) member.id())));
        list("?q=테스트").andExpect(jsonPath("$.total").value(2));
        // %는 글자로 찾는다
        list("?q=%25").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void 기수_상태_역할_정지로_거른다() throws Exception {
        TestMember suspended = fixtures.suspended();
        TestMember leave = fixtures.member();
        jdbc.update("UPDATE member SET status = 'LEAVE', generation = 6 WHERE id = ?", leave.id());

        list("?suspended=true").andExpect(jsonPath("$.items[*].id").value(contains((int) suspended.id())));
        list("?suspended=false").andExpect(jsonPath("$.total").value(2));
        list("?generation=6").andExpect(jsonPath("$.items[*].id").value(contains((int) leave.id())));
        list("?status=LEAVE").andExpect(jsonPath("$.items[*].id").value(contains((int) leave.id())));
        list("?role=STAFF").andExpect(jsonPath("$.items[*].id").value(contains((int) staff.id())));
        list("?size=51").andExpect(status().isBadRequest());
    }

    @Test
    void 이메일을_보면_열람_기록이_남고_주소는_기록하지_않는다() throws Exception {
        TestMember member = fixtures.member();

        mvc.perform(get("/api/v1/ops/members/{id}/email", member.id()).cookie(login))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value(member.loginId() + "@test.gdghost"));

        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM ops_audit_log");
        assertThat(audit).containsEntry("action", "EMAIL_VIEW").containsEntry("actor_id", staff.id())
                .containsEntry("target_member_id", member.id()).containsEntry("detail", null);
    }

    @Test
    void 탈퇴했거나_없는_부원의_이메일은_404() throws Exception {
        mvc.perform(get("/api/v1/ops/members/{id}/email", fixtures.withdrawn().id()).cookie(login))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ops/members/{id}/email", Long.MAX_VALUE).cookie(login))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isZero();
    }

    @Test
    void 부원별_운영_기록은_그_부원_것만_최신순() throws Exception {
        TestMember member = fixtures.member();
        TestMember other = fixtures.member();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        audit(member.id(), "SUSPEND", "{\"reason\": \"외부인\"}", now.minusDays(1));
        audit(member.id(), "EMAIL_VIEW", null, now);
        audit(other.id(), "EMAIL_VIEW", null, now);

        mvc.perform(get("/api/v1/ops/members/{id}/audit-logs", member.id()).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].action").value(contains("EMAIL_VIEW", "SUSPEND")))
                .andExpect(jsonPath("$.items[1].detail.reason").value("외부인"))
                .andExpect(jsonPath("$.items[1].actor.id").value(staff.id()))
                .andExpect(jsonPath("$.items[1].actor.nickname").value(staff.nickname()))
                .andExpect(jsonPath("$.items[1].actor.staffTitle").value("회장"))
                .andExpect(jsonPath("$.items[1].createdAt").exists());
        // 조회 자체는 기록하지 않는다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isEqualTo(3);
    }

    @Test
    void 탈퇴한_부원의_기록도_보고_없는_부원은_404() throws Exception {
        TestMember withdrawn = fixtures.withdrawn();
        audit(withdrawn.id(), "SUSPEND", null, LocalDateTime.now(ZoneOffset.UTC));

        mvc.perform(get("/api/v1/ops/members/{id}/audit-logs", withdrawn.id()).cookie(login))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/ops/members/{id}/audit-logs", Long.MAX_VALUE).cookie(login))
                .andExpect(status().isNotFound());
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get("/api/v1/ops/members" + query).cookie(login));
    }

    private void audit(long targetId, String action, String detail, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO ops_audit_log (actor_id, action, target_member_id, detail, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, staff.id(), action, targetId, detail, createdAt);
    }

}
