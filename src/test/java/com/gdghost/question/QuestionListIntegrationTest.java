package com.gdghost.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

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

/** 질문 목록 */
@IntegrationTest
class QuestionListIntegrationTest {

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
    void 최신순이고_삭제_숨김은_빠진다() throws Exception {
        TestMember other = fixtures.member();
        long first = fixtures.question(other.id(), false);
        long deleted = fixtures.question(other.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
        long hidden = fixtures.question(other.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", hidden);
        long last = fixtures.question(other.id(), false);

        mvc.perform(get("/api/v1/questions").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) last, (int) first)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 커서로_다음_페이지를_읽는다() throws Exception {
        long q1 = fixtures.question(me.id(), false);
        long q2 = fixtures.question(me.id(), false);
        long q3 = fixtures.question(me.id(), false);

        String body = mvc.perform(get("/api/v1/questions").param("size", "2").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) q3, (int) q2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.nextCursor");

        mvc.perform(get("/api/v1/questions").param("size", "2").param("cursor", cursor).cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) q1)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 잘못된_커서는_400() throws Exception {
        mvc.perform(get("/api/v1/questions").param("cursor", "bm90LWEtbnVtYmVy").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    @Test
    void 분류_미답변_내_질문으로_거른다() throws Exception {
        TestMember other = fixtures.member();
        long job = fixtures.question(other.id(), false);
        long study = fixtures.question(other.id(), false);
        jdbc.update("UPDATE question SET category = 'STUDY', state = 'ANSWERED' WHERE id = ?", study);
        long mineNamed = fixtures.question(me.id(), false);
        long mineAnonymous = fixtures.question(me.id(), true);

        mvc.perform(get("/api/v1/questions").param("category", "STUDY").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) study)));
        mvc.perform(get("/api/v1/questions").param("unanswered", "true").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) mineAnonymous, (int) mineNamed, (int) job)));
        mvc.perform(get("/api/v1/questions").param("mine", "true").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) mineAnonymous, (int) mineNamed)));
    }

    @Test
    void 작성자는_실명_익명_탈퇴_세_모양() throws Exception {
        TestMember staff = fixtures.staff();
        TestMember withdrawn = fixtures.withdrawn();
        long named = fixtures.question(staff.id(), false);
        long anonymousOfOther = fixtures.question(staff.id(), true);
        long anonymousMine = fixtures.question(me.id(), true);
        long ofWithdrawn = fixtures.question(withdrawn.id(), false);

        String body = mvc.perform(get("/api/v1/questions").cookie(login))
                .andExpect(jsonPath("$.items[3].id").value(named))
                .andExpect(jsonPath("$.items[3].author.anonymous").value(false))
                .andExpect(jsonPath("$.items[3].author.id").value(staff.id()))
                .andExpect(jsonPath("$.items[3].author.nickname").value(staff.nickname()))
                .andExpect(jsonPath("$.items[3].author.isStaff").value(true))
                .andExpect(jsonPath("$.items[2].id").value(anonymousOfOther))
                .andExpect(jsonPath("$.items[2].author.label").value("익명"))
                .andExpect(jsonPath("$.items[2].author.id").doesNotExist())
                .andExpect(jsonPath("$.items[2].author.mine").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(anonymousMine))
                .andExpect(jsonPath("$.items[1].author.mine").value(true))
                .andExpect(jsonPath("$.items[0].id").value(ofWithdrawn))
                .andExpect(jsonPath("$.items[0].author.id").isEmpty())
                .andExpect(jsonPath("$.items[0].author.nickname").value("탈퇴한 부원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 목록에는 본문을 담지 않는다
        assertThat(body).doesNotContain("질문 본문");
    }

    @Test
    void 답변_수는_답글_삭제_답변을_빼고_미리보기는_먼저_답한_3명() throws Exception {
        long question = fixtures.question(me.id(), true);
        TestMember a = fixtures.member();
        TestMember b = fixtures.member();
        TestMember c = fixtures.member();
        TestMember d = fixtures.member();
        jdbc.update("INSERT INTO question_anon_alias (question_id, member_id, alias_no) VALUES (?, ?, 1)",
                question, b.id());
        long first = fixtures.answer(question, a.id(), false, null);
        fixtures.answer(question, a.id(), false, null);                 // 같은 사람은 한 번만
        fixtures.answer(question, me.id(), false, first);               // 답글
        long deleted = fixtures.answer(question, d.id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        fixtures.answer(question, b.id(), true, null);                  // 익명1
        fixtures.answer(question, c.id(), false, null);
        fixtures.answer(question, d.id(), false, null);                 // 넷째라 빠짐

        mvc.perform(get("/api/v1/questions").cookie(login))
                .andExpect(jsonPath("$.items[0].answerCount").value(5))
                .andExpect(jsonPath("$.items[0].answererPreview", hasSize(3)))
                .andExpect(jsonPath("$.items[0].answererPreview[0].id").value(a.id()))
                .andExpect(jsonPath("$.items[0].answererPreview[1].label").value("익명1"))
                .andExpect(jsonPath("$.items[0].answererPreview[1].id").doesNotExist())
                .andExpect(jsonPath("$.items[0].answererPreview[2].id").value(c.id()));
    }

    @Test
    void 비회원은_제목까지만_받는다() throws Exception {
        TestMember withdrawn = fixtures.withdrawn();
        long named = fixtures.question(me.id(), false);
        long anonymous = fixtures.question(me.id(), true);
        long ofWithdrawn = fixtures.question(withdrawn.id(), false);
        fixtures.answer(named, fixtures.member().id(), false, null);

        String body = mvc.perform(get("/api/v1/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) ofWithdrawn, (int) anonymous, (int) named)))
                .andExpect(jsonPath("$.items[0].author.nickname").value("탈퇴한 부원"))
                .andExpect(jsonPath("$.items[1].author.label").value("익명"))
                .andExpect(jsonPath("$.items[1].author.mine").doesNotExist())
                .andExpect(jsonPath("$.items[2].author.nickname").value(me.nickname()))
                .andExpect(jsonPath("$.items[2].author.id").doesNotExist())
                .andExpect(jsonPath("$.items[2].answerCount").value(1))
                .andExpect(jsonPath("$.items[2].title").exists())
                .andExpect(jsonPath("$.items[2].createdAt").exists())
                .andExpect(jsonPath("$.items[2].answererPreview").doesNotExist())
                .andExpect(jsonPath("$.items[2].viewCount").doesNotExist())
                .andExpect(jsonPath("$.items[2].state").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("본문");
    }

    @Test
    void 비회원이_내_질문을_요청하면_401() throws Exception {
        mvc.perform(get("/api/v1/questions").param("mine", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
