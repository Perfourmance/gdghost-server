package com.gdghost.member;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** 연락 수단 저장 */
@IntegrationTest
class ContactsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember member;
    Cookie login;

    @BeforeEach
    void setUp() {
        member = fixtures.member();
        login = fixtures.loginCookie(member);
    }

    @Test
    void 통째로_교체하고_빠진_종류는_지운다() throws Exception {
        jdbc.update("INSERT INTO member_contact (member_id, type, value) VALUES (?, 'INSTA', '@old')", member.id());

        putContacts("""
                {"contacts":[{"type":"LINKEDIN","value":" in/serim ","isPublic":true},
                             {"type":"PHONE","value":"010-1234-5678"}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts", hasSize(2)))
                .andExpect(jsonPath("$.contacts[0].type").value("LINKEDIN"))
                .andExpect(jsonPath("$.contacts[0].value").value("in/serim"))
                .andExpect(jsonPath("$.contacts[0].isPublic").value(true))
                .andExpect(jsonPath("$.contacts[1].type").value("PHONE"))
                .andExpect(jsonPath("$.contacts[1].isPublic").value(false));

        assertThat(jdbc.queryForList("SELECT type FROM member_contact WHERE member_id = ?", String.class, member.id()))
                .containsExactlyInAnyOrder("LINKEDIN", "PHONE");
    }

    @Test
    void 빈_배열이면_모두_지운다() throws Exception {
        jdbc.update("INSERT INTO member_contact (member_id, type, value) VALUES (?, 'INSTA', '@old')", member.id());

        putContacts("{\"contacts\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts", hasSize(0)));
    }

    @Test
    void 같은_종류를_두_번_보내면_400() throws Exception {
        putContacts("""
                {"contacts":[{"type":"KAKAO","value":"a"},{"type":"KAKAO","value":"b"}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("contacts[1].type"));
    }

    @Test
    void 전화번호는_숫자와_하이픈만() throws Exception {
        putContacts("{\"contacts\":[{\"type\":\"PHONE\",\"value\":\"010 1234 5678\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contacts[0].value"));
    }

    @Test
    void 값이_비었거나_100자를_넘으면_400() throws Exception {
        putContacts("{\"contacts\":[{\"type\":\"INSTA\",\"value\":\" \"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        putContacts("{\"contacts\":[{\"type\":\"INSTA\",\"value\":\"" + "a".repeat(101) + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions putContacts(String json) throws Exception {
        return mvc.perform(put("/api/v1/me/contacts").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

}
