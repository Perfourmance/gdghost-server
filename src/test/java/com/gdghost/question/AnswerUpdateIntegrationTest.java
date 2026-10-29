package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** 답변 · 답글 수정 */
@IntegrationTest
class AnswerUpdateIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;
    long question;
    long answer;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
        question = fixtures.question(fixtures.member().id(), true);
        answer = fixtures.answer(question, me.id(), true, null);
    }

    @Test
    void 본문을_바꾸고_editedAt을_찍어_하위_답글과_함께_돌려준다() throws Exception {
        long reply = fixtures.answer(question, fixtures.member().id(), false, answer);
        jdbc.update("UPDATE question SET adopted_answer_id = ?, state = 'ADOPTED' WHERE id = ?", answer, question);

        patchAnswer(answer, login, "{\"body\":\"  고친 답변 본문 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(answer))
                .andExpect(jsonPath("$.body").value("고친 답변 본문"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty())
                .andExpect(jsonPath("$.author.anonymous").value(true))
                .andExpect(jsonPath("$.adopted").value(true))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.replies[0].id").value(reply));

        assertThat(jdbc.queryForObject("SELECT adopted_answer_id FROM question WHERE id = ?", Long.class, question))
                .isEqualTo(answer);
    }

    @Test
    void 본문이_그대로면_editedAt을_찍지_않는다() throws Exception {
        patchAnswer(answer, login, "{\"body\":\" 답변 본문 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAt").isEmpty());
    }

    @Test
    void 답변은_5자_답글은_1자부터() throws Exception {
        long reply = fixtures.answer(question, me.id(), false, answer);

        patchAnswer(answer, login, "{\"body\":\"짧은답\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        patchAnswer(reply, login, "{\"body\":\"  \"}")
                .andExpect(status().isBadRequest());
        patchAnswer(reply, login, "{\"body\":\"넵\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 남의_답변은_403_지운_답변이나_질문은_404() throws Exception {
        patchAnswer(answer, fixtures.loginCookie(fixtures.member()), "{\"body\":\"남의 답변 수정\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));

        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", question);
        patchAnswer(answer, login, "{\"body\":\"지운 질문의 답변\"}")
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE question SET deleted_at = NULL, deleted_by = NULL WHERE id = ?", question);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", answer);
        patchAnswer(answer, login, "{\"body\":\"숨긴 답변 수정\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        patchAnswer(answer + 1000, login, "{\"body\":\"없는 답변 수정\"}")
                .andExpect(status().isNotFound());
    }

    private ResultActions patchAnswer(long id, Cookie cookie, String json) throws Exception {
        return mvc.perform(patch("/api/v1/answers/{id}", id).cookie(cookie).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

}
