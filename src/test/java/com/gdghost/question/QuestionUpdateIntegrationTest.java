package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** 질문 수정 */
@IntegrationTest
class QuestionUpdateIntegrationTest {

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
    void 보낸_필드만_바꾸고_상세_모양으로_돌려준다() throws Exception {
        String title = jdbc.queryForObject("SELECT title FROM question WHERE id = ?", String.class, question);

        patchQuestion(question, login, "{\"category\":\"STUDY\",\"body\":\"  새 본문 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(question))
                .andExpect(jsonPath("$.category").value("STUDY"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.body").value("새 본문"))
                .andExpect(jsonPath("$.editedAfterAnswer").value(false))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.answers").isArray());

        assertThat(row().get("updated_at")).isNotNull();
    }

    @Test
    void 빈_본문은_본문을_지운다() throws Exception {
        patchQuestion(question, login, "{\"body\":\"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").isEmpty());

        assertThat(row().get("body")).isNull();
    }

    @Test
    void 지우지_않은_답변이나_답글이_있을_때_고치면_수정됨() throws Exception {
        TestMember other = fixtures.member();
        long deleted = fixtures.answer(question, other.id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);

        // 지운 답변만 있으면 수정됨이 아니다
        patchQuestion(question, login, "{\"title\":\"첫 수정 제목\"}")
                .andExpect(jsonPath("$.editedAfterAnswer").value(false));

        fixtures.answer(question, other.id(), false, deleted);
        patchQuestion(question, login, "{\"title\":\"두 번째 수정 제목\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAfterAnswer").value(true));
    }

    @Test
    void 바뀐_게_없으면_수정_시각도_수정됨도_그대로() throws Exception {
        fixtures.answer(question, fixtures.member().id(), false, null);
        Map<String, Object> before = row();

        patchQuestion(question, login, "{\"category\":\"" + before.get("category") + "\",\"title\":\" "
                + before.get("title") + " \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAfterAnswer").value(false));

        assertThat(row().get("updated_at")).isNull();
    }

    @Test
    void 익명_여부를_보내거나_아무것도_안_보내거나_제목이_짧으면_400() throws Exception {
        patchQuestion(question, login, "{\"title\":\"제목 수정\",\"anonymous\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchQuestion(question, login, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        patchQuestion(question, login, "{\"title\":\" 가 \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(row().get("updated_at")).isNull();
    }

    @Test
    void 남의_질문은_403_없거나_지운_질문은_404() throws Exception {
        patchQuestion(question, fixtures.loginCookie(fixtures.member()), "{\"title\":\"남의 질문 수정\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));

        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", question);
        patchQuestion(question, login, "{\"title\":\"숨긴 질문 수정\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        patchQuestion(question + 1000, login, "{\"title\":\"없는 질문 수정\"}")
                .andExpect(status().isNotFound());
    }

    private ResultActions patchQuestion(long id, Cookie cookie, String json) throws Exception {
        return mvc.perform(patch("/api/v1/questions/{id}", id).cookie(cookie).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT category, title, body, updated_at FROM question WHERE id = ?", question);
    }

}
