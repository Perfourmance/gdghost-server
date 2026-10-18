package com.gdghost.member;

import static org.hamcrest.Matchers.contains;
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

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 부원 목록 */
@IntegrationTest
class MemberListIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
    }

    @Test
    void 기수_내림차순_닉네임_순이고_숨김_탈퇴_정지는_빠진다() throws Exception {
        TestMember senior = member(2, "가나다");
        TestMember sameGenB = member(6, "나비");
        TestMember sameGenA = member(6, "가방");
        TestMember hidden = fixtures.member();
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());
        fixtures.withdrawn();
        fixtures.suspended();
        rename(me, 5, "나");

        mvc.perform(get("/api/v1/members").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id",
                        contains((int) sameGenA.id(), (int) sameGenB.id(), (int) me.id(), (int) senior.id())))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void 한_줄에_관심_분야_답변_수_운영진_여부를_준다() throws Exception {
        TestMember staff = fixtures.staff();
        jdbc.update("UPDATE member SET status_note = '백엔드 2년차', accept_coffee = TRUE, coffee_memo = '메모' WHERE id = ?",
                staff.id());
        jdbc.update("""
                INSERT INTO member_tag (member_id, tag_id)
                SELECT ?, id FROM tag WHERE kind = 'INTEREST' AND name IN ('백엔드', '클라우드')
                """, staff.id());
        long question = fixtures.question(me.id(), false);
        fixtures.answer(question, staff.id(), false, null);
        fixtures.answer(question, staff.id(), false, null);
        fixtures.answer(question, staff.id(), true, null);                    // 익명
        long first = fixtures.answer(question, staff.id(), false, null);
        fixtures.answer(question, staff.id(), false, first);                  // 답글
        long deleted = fixtures.answer(question, staff.id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        mvc.perform(get("/api/v1/members").cookie(login))
                .andExpect(jsonPath("$.items[?(@.id == " + staff.id() + ")].interestTags[*]",
                        contains("백엔드", "클라우드")))
                .andExpect(jsonPath("$.items[?(@.id == " + staff.id() + ")].answerCount").value(3))
                .andExpect(jsonPath("$.items[?(@.id == " + staff.id() + ")].isStaff").value(true))
                .andExpect(jsonPath("$.items[?(@.id == " + staff.id() + ")].statusNote").value("백엔드 2년차"))
                .andExpect(jsonPath("$.items[?(@.id == " + staff.id() + ")].acceptCoffee").value(true))
                .andExpect(jsonPath("$.items[?(@.id == " + me.id() + ")].answerCount").value(0))
                .andExpect(jsonPath("$.items[?(@.id == " + me.id() + ")].isStaff").value(false))
                .andExpect(jsonPath("$.items[?(@.id == " + me.id() + ")].interestTags", contains(hasSize(0))));
    }

    @Test
    void 기수와_재학_상태로_거른다() throws Exception {
        TestMember graduated = member(2, "졸업생");
        jdbc.update("UPDATE member SET status = 'GRADUATED' WHERE id = ?", graduated.id());
        member(2, "재학생");

        mvc.perform(get("/api/v1/members").param("generation", "2").param("status", "GRADUATED").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) graduated.id())));
    }

    @Test
    void 커서로_다음_페이지를_이어서_준다() throws Exception {
        rename(me, 9, "마지막");
        TestMember a = member(7, "가");
        TestMember b = member(7, "나");
        TestMember c = member(3, "다");

        String body = mvc.perform(get("/api/v1/members").param("size", "2").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) me.id(), (int) a.id())))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.nextCursor");

        mvc.perform(get("/api/v1/members").param("size", "2").param("cursor", cursor).cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) b.id(), (int) c.id())))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 잘못된_커서와_크기는_400() throws Exception {
        mvc.perform(get("/api/v1/members").param("cursor", "!!!").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/members").param("cursor", "bm9wZQ").cookie(login))   // "nope"
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/members").param("size", "51").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private TestMember member(int generation, String nickname) {
        TestMember member = fixtures.member();
        rename(member, generation, nickname);
        return member;
    }

    private void rename(TestMember member, int generation, String nickname) {
        jdbc.update("UPDATE member SET generation = ?, nickname = ? WHERE id = ?", generation, nickname, member.id());
    }

}
