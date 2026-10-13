package com.gdghost.invite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class InviteCodeMembersIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    Cookie login;

    @BeforeEach
    void setUp() {
        login = fixtures.loginCookie(fixtures.staff());
    }

    @Test
    void 그_코드로_가입한_부원을_보여준다() throws Exception {
        long codeId = fixtures.inviteCode(5);
        TestMember joined = joinWith(fixtures.member(), codeId);
        TestMember suspended = joinWith(fixtures.suspended(), codeId);
        joinWith(fixtures.withdrawn(), codeId);
        joinWith(fixtures.member(), fixtures.inviteCode(6));
        jdbc.update("UPDATE member SET student_no = '24' WHERE id = ?", joined.id());

        mvc.perform(get("/api/v1/ops/invite-codes/{codeId}/members", codeId).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(joined.id()))
                .andExpect(jsonPath("$.items[0].nickname").value(joined.nickname()))
                .andExpect(jsonPath("$.items[0].name").isNotEmpty())
                .andExpect(jsonPath("$.items[0].studentNo").value("24"))
                .andExpect(jsonPath("$.items[0].joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].id").value(suspended.id()));
    }

    @Test
    void 가입자가_없으면_빈_목록() throws Exception {
        mvc.perform(get("/api/v1/ops/invite-codes/{codeId}/members", fixtures.inviteCode(5)).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 없는_코드면_404() throws Exception {
        mvc.perform(get("/api/v1/ops/invite-codes/{codeId}/members", 999_999).cookie(login))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private TestMember joinWith(TestMember member, long codeId) {
        jdbc.update("UPDATE member SET invite_code_id = ? WHERE id = ?", codeId, member.id());
        return member;
    }

}
