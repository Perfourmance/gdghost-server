package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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

/** 답변 채택 · 채택 취소 */
@IntegrationTest
class AdoptionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    Cookie login;
    long question;
    long first;
    long second;

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        login = fixtures.loginCookie(author);
        question = fixtures.question(author.id(), false);
        first = fixtures.answer(question, fixtures.member().id(), false, null);
        second = fixtures.answer(question, fixtures.member().id(), false, null);
        jdbc.update("UPDATE question SET state = 'ANSWERED' WHERE id = ?", question);
    }

    @Test
    void 채택하면_ADOPTED이고_다시_채택하면_바뀐다() throws Exception {
        adopt(question, login, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ADOPTED"))
                .andExpect(jsonPath("$.adoptedAnswerId").value(first));

        adopt(question, login, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adoptedAnswerId").value(second));

        // 상세에서 채택 답변이 맨 위
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(jsonPath("$.adoptedAnswerId").value(second))
                .andExpect(jsonPath("$.answers[0].id").value(second))
                .andExpect(jsonPath("$.answers[0].adopted").value(true))
                .andExpect(jsonPath("$.answers[1].adopted").value(false));
    }

    @Test
    void 답글_지운_답변_다른_질문의_답변_없는_답변은_400_ADOPT_INVALID() throws Exception {
        long reply = fixtures.answer(question, fixtures.member().id(), false, first);
        long other = fixtures.answer(fixtures.question(author.id(), false), fixtures.member().id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", second);

        for (long answerId : new long[] {reply, other, second, first + 1000}) {
            adopt(question, login, answerId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ADOPT_INVALID"));
        }
        assertThat(questionRow().get("state")).isEqualTo("ANSWERED");
    }

    @Test
    void answerId가_없으면_400() throws Exception {
        mvc.perform(put("/api/v1/questions/{id}/adoption", question).cookie(login).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 취소하면_ANSWERED이고_채택이_없어도_204() throws Exception {
        adopt(question, login, first).andExpect(status().isOk());

        cancel(question, login).andExpect(status().isNoContent());
        Map<String, Object> row = questionRow();
        assertThat(row.get("state")).isEqualTo("ANSWERED");
        assertThat(row.get("adopted_answer_id")).isNull();

        cancel(question, login).andExpect(status().isNoContent());
        assertThat(questionRow().get("state")).isEqualTo("ANSWERED");
    }

    @Test
    void 질문자가_아니면_403_질문이_없거나_지워졌으면_404() throws Exception {
        Cookie answerer = fixtures.loginCookie(fixtures.member());
        adopt(question, answerer, first)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        cancel(question, answerer).andExpect(status().isForbidden());

        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", question);
        adopt(question, login, first).andExpect(status().isNotFound());
        cancel(question, login).andExpect(status().isNotFound());
        cancel(question + 1000, login)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private ResultActions adopt(long questionId, Cookie cookie, long answerId) throws Exception {
        return mvc.perform(put("/api/v1/questions/{id}/adoption", questionId).cookie(cookie).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"answerId\":" + answerId + "}"));
    }

    private ResultActions cancel(long questionId, Cookie cookie) throws Exception {
        return mvc.perform(delete("/api/v1/questions/{id}/adoption", questionId).cookie(cookie).with(csrfToken()));
    }

    private Map<String, Object> questionRow() {
        return jdbc.queryForMap("SELECT state, adopted_answer_id FROM question WHERE id = ?", question);
    }

}
