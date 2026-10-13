package com.gdghost.invite;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
class InviteCodeListIntegrationTest {

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
    void 최신순으로_상태와_발급한_운영진을_보여준다() throws Exception {
        long bootstrap = fixtures.inviteCode(5);
        long revoked = fixtures.inviteCode(5);
        long active = fixtures.inviteCode(6);
        jdbc.update("UPDATE invite_code SET revoked_at = ? WHERE id = ?", LocalDateTime.now(ZoneOffset.UTC), revoked);
        jdbc.update("UPDATE invite_code SET created_by = ? WHERE id IN (?, ?)", staff.id(), revoked, active);

        mvc.perform(get("/api/v1/ops/invite-codes").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(active))
                .andExpect(jsonPath("$.items[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].createdBy.nickname").value(staff.nickname()))
                .andExpect(jsonPath("$.items[1].id").value(revoked))
                .andExpect(jsonPath("$.items[1].state").value("REVOKED"))
                .andExpect(jsonPath("$.items[2].id").value(bootstrap))
                .andExpect(jsonPath("$.items[2].createdBy").doesNotExist());
    }

    @Test
    void 페이지를_나눈다() throws Exception {
        long oldest = fixtures.inviteCode(5);
        fixtures.inviteCode(5);
        fixtures.inviteCode(5);

        mvc.perform(get("/api/v1/ops/invite-codes").param("page", "1").param("size", "2").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldest));
    }

    @Test
    void 한_페이지는_50개까지() throws Exception {
        mvc.perform(get("/api/v1/ops/invite-codes").param("size", "51").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

}
