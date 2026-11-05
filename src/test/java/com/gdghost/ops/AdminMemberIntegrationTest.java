package com.gdghost.ops;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class AdminMemberIntegrationTest {

    private static final String URL = "/api/v1/admin/members";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 개발자는_숨긴_부원과_정지된_부원도_보고_탈퇴자는_빠진다() throws Exception {
        TestMember hidden = fixtures.member();
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());
        TestMember suspended = fixtures.suspended();
        TestMember withdrawn = fixtures.withdrawn();

        mvc.perform(get(URL).param("size", "50").cookie(fixtures.loginCookie(fixtures.dev())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItems((int) hidden.id(), (int) suspended.id())))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) withdrawn.id()))))
                .andExpect(jsonPath("$.items[?(@.id == %d)].hiddenFromList".formatted(hidden.id())).value(true))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());
    }

    @Test
    void q로_이름_닉네임_아이디를_찾는다() throws Exception {
        TestMember target = fixtures.member();
        fixtures.member();

        mvc.perform(get(URL).param("q", target.loginId()).cookie(fixtures.loginCookie(fixtures.dev())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(target.id()));
        mvc.perform(get(URL).param("q", "가".repeat(51)).cookie(fixtures.loginCookie(fixtures.dev())))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 운영진과_부원은_404() throws Exception {
        mvc.perform(get(URL).cookie(fixtures.loginCookie(fixtures.staff()))).andExpect(status().isNotFound());
        mvc.perform(get(URL).cookie(fixtures.loginCookie(fixtures.member()))).andExpect(status().isNotFound());
    }

}
