package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** 답변 · 답글 삭제 */
@IntegrationTest
class AnswerDeleteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;
    long question;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
        question = fixtures.question(fixtures.member().id(), false);
        jdbc.update("UPDATE question SET state = 'ANSWERED' WHERE id = ?", question);
    }

    @Test
    void 답글이_남은_답변은_자리를_남기고_다른_답변이_있으면_ANSWERED_그대로() throws Exception {
        long answer = fixtures.answer(question, me.id(), false, null);
        long reply = fixtures.answer(question, fixtures.member().id(), false, answer);
        fixtures.answer(question, fixtures.member().id(), false, null);

        deleteAnswer(answer, login).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM answer WHERE id = ?", String.class, answer))
                .isEqualTo("AUTHOR");
        assertThat(questionRow().get("state")).isEqualTo("ANSWERED");
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.answers[0].id").value(answer))
                .andExpect(jsonPath("$.answers[0].deletedBy").value("AUTHOR"))
                .andExpect(jsonPath("$.answers[0].body").isEmpty())
                .andExpect(jsonPath("$.answers[0].replies[0].id").value(reply));
    }

    @Test
    void 채택된_답변을_지우면_채택이_풀리고_남은_답변이_없으면_WAITING() throws Exception {
        long answer = fixtures.answer(question, me.id(), false, null);
        // 답글만 남아도 답변은 없는 셈이다
        fixtures.answer(question, fixtures.member().id(), false, answer);
        jdbc.update("UPDATE question SET adopted_answer_id = ?, state = 'ADOPTED' WHERE id = ?", answer, question);

        deleteAnswer(answer, login).andExpect(status().isNoContent());

        Map<String, Object> row = questionRow();
        assertThat(row.get("adopted_answer_id")).isNull();
        assertThat(row.get("state")).isEqualTo("WAITING");
    }

    @Test
    void 채택된_답변을_지워도_다른_답변이_있으면_ANSWERED() throws Exception {
        long adopted = fixtures.answer(question, me.id(), false, null);
        fixtures.answer(question, fixtures.member().id(), false, null);
        jdbc.update("UPDATE question SET adopted_answer_id = ?, state = 'ADOPTED' WHERE id = ?", adopted, question);

        deleteAnswer(adopted, login).andExpect(status().isNoContent());

        assertThat(questionRow().get("state")).isEqualTo("ANSWERED");
    }

    @Test
    void 채택된_다른_답변이_있으면_ADOPTED_그대로() throws Exception {
        long adopted = fixtures.answer(question, fixtures.member().id(), false, null);
        long mine = fixtures.answer(question, me.id(), false, null);
        jdbc.update("UPDATE question SET adopted_answer_id = ?, state = 'ADOPTED' WHERE id = ?", adopted, question);

        deleteAnswer(mine, login).andExpect(status().isNoContent());

        Map<String, Object> row = questionRow();
        assertThat(row.get("state")).isEqualTo("ADOPTED");
        assertThat(((Number) row.get("adopted_answer_id")).longValue()).isEqualTo(adopted);
    }

    @Test
    void 답글을_지워도_질문_상태는_그대로() throws Exception {
        long answer = fixtures.answer(question, fixtures.member().id(), false, null);
        long reply = fixtures.answer(question, me.id(), false, answer);

        deleteAnswer(reply, login).andExpect(status().isNoContent());

        assertThat(questionRow().get("state")).isEqualTo("ANSWERED");
    }

    @Test
    void 남의_답변은_403_이미_지웠거나_질문이_지워졌으면_404() throws Exception {
        long answer = fixtures.answer(question, me.id(), false, null);

        deleteAnswer(answer, fixtures.loginCookie(fixtures.member()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));

        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", question);
        deleteAnswer(answer, login).andExpect(status().isNotFound());

        jdbc.update("UPDATE question SET deleted_at = NULL, deleted_by = NULL WHERE id = ?", question);
        deleteAnswer(answer, login).andExpect(status().isNoContent());
        deleteAnswer(answer, login)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions deleteAnswer(long id, Cookie cookie) throws Exception {
        return mvc.perform(delete("/api/v1/answers/{id}", id).cookie(cookie).with(csrfToken()));
    }

    private Map<String, Object> questionRow() {
        return jdbc.queryForMap("SELECT state, adopted_answer_id FROM question WHERE id = ?", question);
    }

}
