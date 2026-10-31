package com.gdghost.auth;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.common.security.SessionCookie;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** 로그아웃 · 내 세션 확인 */
@IntegrationTest
class SessionIntegrationTest {

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
    void 내_세션_확인에_확인하지_않은_필독_공지_수가_담긴다() throws Exception {
        TestMember staff = fixtures.staff();
        TestMember member = fixtures.member();
        fixtures.notice(staff.id());
        long unconfirmed = fixtures.notice(staff.id());
        long confirmed = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET is_must_read = TRUE WHERE id IN (?, ?)", unconfirmed, confirmed);
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, read_at, confirmed_at) VALUES (?, ?, NOW(6), NOW(6))",
                confirmed, member.id());

        mvc.perform(get("/api/v1/auth/me").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.unconfirmedMustReadCount").value(1));
    }

    @Test
    void 내_세션을_확인하면_Me를_주고_마지막_접속을_남긴다() throws Exception {
        jdbc.update("UPDATE member SET bio = '자기소개' WHERE id = ?", member.id());
        jdbc.update("""
                INSERT INTO member_tag (member_id, tag_id)
                SELECT ?, id FROM tag WHERE kind = 'INTEREST' AND name = '백엔드'
                """, member.id());
        jdbc.update("INSERT INTO member_contact (member_id, type, value) VALUES (?, 'INSTA', '@serim')", member.id());

        mvc.perform(get("/api/v1/auth/me").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.id()))
                .andExpect(jsonPath("$.loginId").value(member.loginId()))
                .andExpect(jsonPath("$.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.generation").value(5))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.unreadNotificationCount").value(0))
                .andExpect(jsonPath("$.unconfirmedMustReadCount").value(0))
                // 관심 분야 · 자기소개 · 연락 수단
                .andExpect(jsonPath("$.profileCompleteness.filled").value(3))
                .andExpect(jsonPath("$.profileCompleteness.total").value(6))
                .andExpect(jsonPath("$.profileCompleteness.missing", contains("SKILL", "EXPERIENCE", "STATUS_NOTE")));

        assertThat(jdbc.queryForObject("SELECT last_seen_at IS NOT NULL FROM member WHERE id = ?", Boolean.class,
                member.id())).isTrue();
    }

    @Test
    void 운영진이면_직함도_준다() throws Exception {
        TestMember staff = fixtures.staff();
        jdbc.update("UPDATE member SET staff_title = '회장' WHERE id = ?", staff.id());

        mvc.perform(get("/api/v1/auth/me").cookie(fixtures.loginCookie(staff)))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.staffTitle").value("회장"));
    }

    @Test
    void 비회원이면_401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 정지되면_가지고_있던_쿠키로도_401() throws Exception {
        jdbc.update("UPDATE member SET suspended_at = UTC_TIMESTAMP(6) WHERE id = ?", member.id());

        mvc.perform(get("/api/v1/auth/me").cookie(login))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));
    }

    @Test
    void 로그아웃하면_이_기기만_끝난다() throws Exception {
        Cookie otherDevice = fixtures.loginCookie(member);

        mvc.perform(post("/api/v1/auth/logout").cookie(login).with(csrfToken()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));

        mvc.perform(get("/api/v1/auth/me").cookie(login)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(otherDevice)).andExpect(status().isOk());
    }

}
