package com.gdghost.member;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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

/** 프로필 상세 */
@IntegrationTest
class MemberProfileIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    TestMember other;
    Cookie login;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        other = fixtures.member();
        login = fixtures.loginCookie(me);
        jdbc.update("INSERT INTO member_contact (member_id, type, value, is_public) VALUES (?, 'LINKEDIN', 'in/x', TRUE)",
                other.id());
        jdbc.update("INSERT INTO member_contact (member_id, type, value, is_public) VALUES (?, 'KAKAO', 'kakao_x', FALSE)",
                other.id());
    }

    @Test
    void 실명_학과_학번과_태그_공개_연락_수단을_준다() throws Exception {
        jdbc.update("""
                UPDATE member SET student_no = '19', department = '컴퓨터과학과', bio = '소개', accept_coffee = TRUE,
                                  coffee_memo = '메모'
                WHERE id = ?
                """, other.id());
        jdbc.update("""
                INSERT INTO member_tag (member_id, tag_id)
                SELECT ?, t.id FROM tag t LEFT JOIN tag p ON p.id = t.parent_id
                WHERE (t.kind = 'INTEREST' AND t.name = '백엔드')
                   OR (t.kind = 'SKILL' AND p.name = '백엔드' AND t.name IN ('Spring', 'DB 설계'))
                   OR (t.kind = 'EXPERIENCE' AND t.name = '인턴')
                """, other.id());

        mvc.perform(get("/api/v1/members/{id}", other.id()).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.id()))
                .andExpect(jsonPath("$.nickname").value(other.nickname()))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.studentNo").value("19"))
                .andExpect(jsonPath("$.department").value("컴퓨터과학과"))
                .andExpect(jsonPath("$.isStaff").value(false))
                .andExpect(jsonPath("$.interests[0].name").value("백엔드"))
                .andExpect(jsonPath("$.interests[0].skills", contains("Spring", "DB 설계")))
                .andExpect(jsonPath("$.experiences", contains("인턴")))
                .andExpect(jsonPath("$.bio").value("소개"))
                // 비공개 항목은 필드 자체가 없다
                .andExpect(jsonPath("$.contacts", hasSize(1)))
                .andExpect(jsonPath("$.contacts[0].type").value("LINKEDIN"))
                .andExpect(jsonPath("$.contacts[0].value").value("in/x"))
                .andExpect(jsonPath("$.contacts[0].isPublic").doesNotExist())
                .andExpect(jsonPath("$.acceptQuestion").value(true))
                .andExpect(jsonPath("$.acceptCoffee").value(true))
                .andExpect(jsonPath("$.coffeeChat.myRequestState").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.loginId").doesNotExist())
                .andExpect(jsonPath("$.coffeeMemo").doesNotExist());
    }

    @Test
    void 본인이_보면_비공개_연락_수단도_보인다() throws Exception {
        mvc.perform(get("/api/v1/members/{id}", other.id()).cookie(fixtures.loginCookie(other)))
                .andExpect(jsonPath("$.contacts[*].type", containsInAnyOrder("LINKEDIN", "KAKAO")));
    }

    @Test
    void 활동_수는_익명_삭제_답글을_빼고_센다() throws Exception {
        fixtures.question(other.id(), false);
        fixtures.question(other.id(), true);
        long deletedQuestion = fixtures.question(other.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deletedQuestion);
        long mine = fixtures.question(me.id(), false);
        long answer = fixtures.answer(mine, other.id(), false, null);
        fixtures.answer(mine, other.id(), true, null);
        fixtures.answer(mine, other.id(), false, answer);
        fixtures.answer(deletedQuestion, other.id(), false, null);
        jdbc.update("INSERT INTO share (author_id, category, title, body) VALUES (?, 'TIP', '팁', '본문')", other.id());
        jdbc.update("INSERT INTO feed_post (author_id, category, body) VALUES (?, 'DAILY', '일상')", other.id());
        jdbc.update("""
                INSERT INTO feed_post (author_id, category, body, deleted_at) VALUES (?, 'DAILY', '삭제', UTC_TIMESTAMP(6))
                """, other.id());

        // 본인 조회도 같은 규칙
        for (Cookie viewer : new Cookie[] { login, fixtures.loginCookie(other) }) {
            mvc.perform(get("/api/v1/members/{id}", other.id()).cookie(viewer))
                    .andExpect(jsonPath("$.stats.answerCount").value(1))
                    .andExpect(jsonPath("$.stats.questionCount").value(1))
                    .andExpect(jsonPath("$.stats.shareCount").value(1))
                    .andExpect(jsonPath("$.stats.feedCount").value(1));
        }
    }

    @Test
    void 목록_숨김이어도_직접_열면_보인다() throws Exception {
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", other.id());

        mvc.perform(get("/api/v1/members/{id}", other.id()).cookie(login))
                .andExpect(status().isOk());
    }

    @Test
    void 없거나_탈퇴_정지한_부원은_404() throws Exception {
        for (long id : new long[] { 999_999, fixtures.withdrawn().id(), fixtures.suspended().id() }) {
            mvc.perform(get("/api/v1/members/{id}", id).cookie(login))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

}
