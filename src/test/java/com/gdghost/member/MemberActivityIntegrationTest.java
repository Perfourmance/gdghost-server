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

/** 프로필 활동 기록 */
@IntegrationTest
class MemberActivityIntegrationTest {

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
    }

    @Test
    void 답변은_최신순으로_질문_제목과_채택_여부를_준다() throws Exception {
        long q1 = fixtures.question(me.id(), false);
        long q2 = fixtures.question(me.id(), false);
        long adopted = fixtures.answer(q1, other.id(), false, null);
        jdbc.update("UPDATE question SET state = 'ADOPTED', adopted_answer_id = ? WHERE id = ?", adopted, q1);
        fixtures.answer(q2, other.id(), false, null);
        String q1Title = jdbc.queryForObject("SELECT title FROM question WHERE id = ?", String.class, q1);

        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "ANSWER").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].targetId", contains((int) q2, (int) q1)))
                .andExpect(jsonPath("$.items[0].type").value("ANSWER"))
                .andExpect(jsonPath("$.items[0].adopted").value(false))
                .andExpect(jsonPath("$.items[1].adopted").value(true))
                .andExpect(jsonPath("$.items[1].title").value(q1Title))
                .andExpect(jsonPath("$.items[1].snippet").value("답변 본문"))
                .andExpect(jsonPath("$.items[1].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].anonymous").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 익명_글은_남이_볼_때_빠지고_본인은_anonymous와_함께_본다() throws Exception {
        long question = fixtures.question(me.id(), false);
        fixtures.answer(question, other.id(), true, null);
        fixtures.answer(question, other.id(), false, null);
        fixtures.question(other.id(), true);

        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "ANSWER").cookie(login))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "QUESTION").cookie(login))
                .andExpect(jsonPath("$.items", hasSize(0)));

        Cookie self = fixtures.loginCookie(other);
        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "ANSWER").cookie(self))
                .andExpect(jsonPath("$.items[*].anonymous", contains(false, true)));
        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "QUESTION").cookie(self))
                .andExpect(jsonPath("$.items[0].anonymous").value(true));
    }

    @Test
    void 삭제된_글과_답글은_빠진다() throws Exception {
        long question = fixtures.question(me.id(), false);
        long answer = fixtures.answer(question, other.id(), false, null);
        fixtures.answer(question, other.id(), false, answer);
        long deleted = fixtures.question(other.id(), false);
        fixtures.answer(deleted, other.id(), false, null);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "ANSWER").cookie(login))
                .andExpect(jsonPath("$.items[*].targetId", contains((int) question)));
        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "QUESTION").cookie(login))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 질문은_채택되면_adopted() throws Exception {
        long question = fixtures.question(other.id(), false);
        jdbc.update("UPDATE question SET state = 'ADOPTED' WHERE id = ?", question);

        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "QUESTION").cookie(login))
                .andExpect(jsonPath("$.items[0].type").value("QUESTION"))
                .andExpect(jsonPath("$.items[0].targetId").value(question))
                .andExpect(jsonPath("$.items[0].snippet").isNotEmpty())
                .andExpect(jsonPath("$.items[0].adopted").value(true));
    }

    @Test
    void 커서로_다음_페이지를_이어서_준다() throws Exception {
        long q1 = fixtures.question(other.id(), false);
        long q2 = fixtures.question(other.id(), false);
        long q3 = fixtures.question(other.id(), false);

        String body = mvc.perform(get("/api/v1/members/{id}/activities", other.id())
                        .param("type", "QUESTION").param("size", "2").cookie(login))
                .andExpect(jsonPath("$.items[*].targetId", contains((int) q3, (int) q2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "QUESTION").param("size", "2")
                        .param("cursor", JsonPath.<String>read(body, "$.nextCursor")).cookie(login))
                .andExpect(jsonPath("$.items[*].targetId", contains((int) q1)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 공유_피드는_아직_빈_목록() throws Exception {
        for (String type : new String[] { "SHARE", "FEED" }) {
            mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", type).cookie(login))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }
    }

    @Test
    void type이_없거나_틀리면_400() throws Exception {
        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/members/{id}/activities", other.id()).param("type", "LIKE").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 없거나_탈퇴_정지한_부원은_404() throws Exception {
        for (long id : new long[] { 999_999, fixtures.withdrawn().id(), fixtures.suspended().id() }) {
            mvc.perform(get("/api/v1/members/{id}/activities", id).param("type", "ANSWER").cookie(login))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

}
