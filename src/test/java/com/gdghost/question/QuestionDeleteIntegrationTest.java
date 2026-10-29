package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 질문 삭제 */
@IntegrationTest
class QuestionDeleteIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember author;
    Cookie login;
    long question;

    @BeforeEach
    void setUp() {
        author = fixtures.member();
        login = fixtures.loginCookie(author);
        question = fixtures.question(author.id(), false);
    }

    @Test
    void 지우면_204이고_답변은_남지만_질문과_함께_안_보인다() throws Exception {
        long answer = fixtures.answer(question, fixtures.member().id(), false, null);

        mvc.perform(delete("/api/v1/questions/{id}", question).cookie(login).with(csrfToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM question WHERE id = ? AND deleted_at IS NOT NULL",
                String.class, question)).isEqualTo("AUTHOR");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM answer WHERE id = ?", Boolean.class, answer))
                .isTrue();
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(login))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/questions").cookie(login))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 남의_질문은_403_이미_지운_질문은_404() throws Exception {
        mvc.perform(delete("/api/v1/questions/{id}", question)
                        .cookie(fixtures.loginCookie(fixtures.member())).with(csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));

        mvc.perform(delete("/api/v1/questions/{id}", question).cookie(login).with(csrfToken()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/questions/{id}", question).cookie(login).with(csrfToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

}
