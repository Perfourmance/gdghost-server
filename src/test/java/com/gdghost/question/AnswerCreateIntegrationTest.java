package com.gdghost.question;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 답변 · 답글 작성 */
@IntegrationTest
class AnswerCreateIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;
    TestMember author;
    Cookie authorLogin;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
        author = fixtures.member();
        authorLogin = fixtures.loginCookie(author);
    }

    @Test
    void 답변은_201_상세_answers_항목_모양이고_첫_답변이면_ANSWERED() throws Exception {
        long question = fixtures.question(author.id(), false);

        answer(question, login, "{\"body\":\"  제 경험상 학점은 \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.parentId").isEmpty())
                .andExpect(jsonPath("$.depth").value(0))
                .andExpect(jsonPath("$.author.id").value(me.id()))
                .andExpect(jsonPath("$.body").value("제 경험상 학점은"))
                .andExpect(jsonPath("$.adopted").value(false))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.replies").isEmpty());

        assertThat(state(question)).isEqualTo("ANSWERED");
    }

    @Test
    void 답글은_부모_depth_더하기_1이고_3에서_멈춘다() throws Exception {
        long question = fixtures.question(author.id(), false);
        long d0 = fixtures.answer(question, author.id(), false, null);
        long d1 = fixtures.answer(question, author.id(), false, d0);
        long d2 = fixtures.answer(question, author.id(), false, d1);
        long d3 = fixtures.answer(question, author.id(), false, d2);
        jdbc.update("UPDATE answer SET depth = 2 WHERE id = ?", d2);
        jdbc.update("UPDATE answer SET depth = 3 WHERE id = ?", d3);

        answer(question, login, "{\"body\":\"네\",\"parentId\":" + d0 + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(d0))
                .andExpect(jsonPath("$.depth").value(1));
        ageAnswers();
        long deep = answerId(answer(question, login, "{\"body\":\"더 깊이\",\"parentId\":" + d3 + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(d3))
                .andExpect(jsonPath("$.depth").value(3)));

        assertThat(jdbc.queryForObject("SELECT depth FROM answer WHERE id = ?", Integer.class, deep)).isEqualTo(3);
    }

    @Test
    void 내_질문엔_답변은_409_답글은_된다() throws Exception {
        long question = fixtures.question(author.id(), false);
        long others = fixtures.answer(question, me.id(), false, null);

        answer(question, authorLogin, "{\"body\":\"제가 답해 볼게요\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_QUESTION_ANSWER"));
        answer(question, authorLogin, "{\"body\":\"감사합니다\",\"parentId\":" + others + "}")
                .andExpect(status().isCreated());
    }

    @Test
    void 익명은_익명_질문에서만_되고_글_단위_번호를_준다() throws Exception {
        long named = fixtures.question(author.id(), false);
        answer(named, login, "{\"body\":\"익명으로 답해요\",\"anonymous\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ANONYMOUS_NOT_ALLOWED"));

        long question = fixtures.question(author.id(), true);
        jdbc.update("INSERT INTO question_anon_alias (question_id, member_id, alias_no) VALUES (?, ?, 0)",
                question, author.id());
        TestMember second = fixtures.member();

        long first = answerId(answer(question, login, "{\"body\":\"첫 익명 답변\",\"anonymous\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.label").value("익명1"))
                .andExpect(jsonPath("$.author.id").doesNotExist())
                .andExpect(jsonPath("$.author.mine").value(true)));
        answer(question, fixtures.loginCookie(second), "{\"body\":\"두 번째 익명 답변\",\"anonymous\":true}")
                .andExpect(jsonPath("$.author.label").value("익명2"));
        answer(question, authorLogin, "{\"body\":\"고마워요\",\"parentId\":" + first + ",\"anonymous\":true}")
                .andExpect(jsonPath("$.author.label").value("익명(글쓴이)"));
        ageAnswers();
        answer(question, login, "{\"body\":\"같은 사람 또 답변\",\"anonymous\":true}")
                .andExpect(jsonPath("$.author.label").value("익명1"));
        // 익명 질문에서도 실명으로 답할 수 있다
        ageAnswers();
        answer(question, login, "{\"body\":\"이번엔 실명으로\"}")
                .andExpect(jsonPath("$.author.id").value(me.id()));
    }

    @Test
    void 동시에_익명으로_달아도_번호가_겹치지_않는다() throws Exception {
        long question = fixtures.question(author.id(), true);
        List<Cookie> logins = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            logins.add(fixtures.loginCookie(fixtures.member()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(logins.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie cookie : logins) {
                Callable<Integer> task = () -> answer(question, cookie, "{\"body\":\"동시에 단 익명 답변\","
                        + "\"anonymous\":true}").andReturn().getResponse().getStatus();
                results.add(pool.submit(task));
            }
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(jdbc.queryForList("SELECT alias_no FROM question_anon_alias WHERE question_id = ? ORDER BY alias_no",
                Integer.class, question)).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void 질문이나_부모가_없거나_지워졌거나_다른_질문이면_404() throws Exception {
        long question = fixtures.question(author.id(), false);
        long other = fixtures.question(author.id(), false);
        long otherAnswer = fixtures.answer(other, author.id(), false, null);
        long deletedAnswer = fixtures.answer(question, author.id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?",
                deletedAnswer);

        answer(question, login, "{\"body\":\"답글\",\"parentId\":" + otherAnswer + "}")
                .andExpect(status().isNotFound());
        answer(question, login, "{\"body\":\"답글\",\"parentId\":" + deletedAnswer + "}")
                .andExpect(status().isNotFound());
        answer(question, login, "{\"body\":\"답글\",\"parentId\":" + (deletedAnswer + 1000) + "}")
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", question);
        answer(question, login, "{\"body\":\"숨긴 질문에 답변\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        answer(question + 1000, login, "{\"body\":\"없는 질문에 답변\"}")
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM answer WHERE author_id = ?", Integer.class, me.id()))
                .isZero();
    }

    @Test
    void 답변은_5자_답글은_1자부터() throws Exception {
        long question = fixtures.question(author.id(), false);
        long parent = fixtures.answer(question, author.id(), false, null);

        answer(question, login, "{\"body\":\" 네 네네 \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        answer(question, login, "{\"body\":\"   \",\"parentId\":" + parent + "}")
                .andExpect(status().isBadRequest());
        answer(question, login, "{\"body\":\"네\",\"parentId\":" + parent + "}")
                .andExpect(status().isCreated());
    }

    @Test
    void 직전_제출_5초_안이면_429_실패한_시도는_세지_않는다() throws Exception {
        long question = fixtures.question(author.id(), false);
        long other = fixtures.question(author.id(), false);

        answer(question, login, "{\"body\":\"짧음\"}").andExpect(status().isBadRequest());
        answer(question, login, "{\"body\":\"첫 번째 답변\"}").andExpect(status().isCreated());
        // 다른 질문이어도 막는다
        answer(other, login, "{\"body\":\"바로 또 답변\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ANSWER_TOO_FAST"))
                .andExpect(header().exists("Retry-After"));

        ageAnswers();
        answer(other, login, "{\"body\":\"5초 지나 답변\"}").andExpect(status().isCreated());
    }

    @Test
    void Idempotency_Key가_없으면_400() throws Exception {
        long question = fixtures.question(author.id(), false);

        mvc.perform(post("/api/v1/questions/{id}/answers", question).cookie(login).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"키 없는 답변\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(IdempotencyAspect.HEADER));
    }

    private ResultActions answer(long questionId, Cookie cookie, String json) throws Exception {
        return mvc.perform(post("/api/v1/questions/{id}/answers", questionId).cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long answerId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.id")).longValue();
    }

    /** 5초 규칙에 걸리지 않게 이미 단 답변을 1분 전으로 */
    private void ageAnswers() {
        jdbc.update("UPDATE answer SET created_at = created_at - INTERVAL 1 MINUTE");
    }

    private String state(long questionId) {
        return jdbc.queryForObject("SELECT state FROM question WHERE id = ?", String.class, questionId);
    }

}
