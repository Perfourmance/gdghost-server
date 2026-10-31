package com.gdghost.report;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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

/** 신고하기 */
@IntegrationTest
class ReportCreateIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember me;
    Cookie login;
    TestMember author;
    long question;

    @BeforeEach
    void setUp() {
        me = fixtures.member();
        login = fixtures.loginCookie(me);
        author = fixtures.member();
        question = fixtures.question(author.id(), false);
    }

    @Test
    void 질문_신고는_201_id이고_OPEN으로_쌓인다() throws Exception {
        ResultActions result = report(login, body("QUESTION", question, "  홍보 링크만 반복해서 올림  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        long id = ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"))
                .longValue();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM report WHERE id = ?", id);
        assertThat(row.get("reporter_id")).isEqualTo(me.id());
        assertThat(row.get("target_type")).isEqualTo("QUESTION");
        assertThat(row.get("target_id")).isEqualTo(question);
        assertThat(row.get("reason")).isEqualTo("홍보 링크만 반복해서 올림");
        assertThat(row.get("state")).isEqualTo("OPEN");
        assertThat(row.get("handled_by")).isNull();
    }

    @Test
    void 답변과_답글도_신고할_수_있다() throws Exception {
        long answer = fixtures.answer(question, author.id(), false, null);
        long reply = fixtures.answer(question, author.id(), false, answer);

        report(login, body("ANSWER", answer, "욕설")).andExpect(status().isCreated());
        report(login, body("ANSWER", reply, "욕설")).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report WHERE target_type = 'ANSWER'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void 같은_대상을_다시_신고하면_409_다른_부원은_가능() throws Exception {
        report(login, body("QUESTION", question, "도배")).andExpect(status().isCreated());

        report(login, body("QUESTION", question, "다른 사유"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));

        TestMember other = fixtures.member();
        report(fixtures.loginCookie(other), body("QUESTION", question, "도배")).andExpect(status().isCreated());
    }

    @Test
    void 본인_글도_신고할_수_있다() throws Exception {
        long mine = fixtures.question(me.id(), true);

        report(login, body("QUESTION", mine, "실수로 개인정보를 적음")).andExpect(status().isCreated());
    }

    @Test
    void 없거나_지워진_대상은_404() throws Exception {
        report(login, body("QUESTION", question + 1000, "사유")).andExpect(status().isNotFound());
        report(login, body("ANSWER", 999_999, "사유")).andExpect(status().isNotFound());

        long deletedAnswer = fixtures.answer(question, author.id(), false, null);
        jdbc.update("UPDATE answer SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?",
                deletedAnswer);
        report(login, body("ANSWER", deletedAnswer, "사유"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        long answer = fixtures.answer(question, author.id(), false, null);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'STAFF' WHERE id = ?", question);
        report(login, body("QUESTION", question, "사유")).andExpect(status().isNotFound());
        report(login, body("ANSWER", answer, "사유")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report", Integer.class)).isZero();
    }

    @Test
    void 사유가_비었거나_200자를_넘으면_400() throws Exception {
        report(login, body("QUESTION", question, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        report(login, "{\"targetType\":\"QUESTION\",\"targetId\":" + question + "}")
                .andExpect(status().isBadRequest());
        report(login, body("QUESTION", question, "가".repeat(201))).andExpect(status().isBadRequest());

        report(login, body("QUESTION", question, "가".repeat(200))).andExpect(status().isCreated());
    }

    @Test
    void 대상_종류가_없거나_아직_없는_기능이거나_모르는_값이면_400() throws Exception {
        report(login, "{\"targetId\":" + question + ",\"reason\":\"사유\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetType"));
        report(login, body("SHARE_NOTE", question, "사유"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("targetType"));
        report(login, body("MEMBER", question, "사유")).andExpect(status().isBadRequest());
        report(login, "{\"targetType\":\"QUESTION\",\"reason\":\"사유\"}").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report", Integer.class)).isZero();
    }

    @Test
    void 부원당_분당_5회를_넘으면_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            long target = fixtures.question(author.id(), false);
            report(login, body("QUESTION", target, "사유")).andExpect(status().isCreated());
        }

        report(login, body("QUESTION", question, "사유"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void Idempotency_Key가_없으면_400() throws Exception {
        mvc.perform(post("/api/v1/reports").cookie(login).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body("QUESTION", question, "사유")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(IdempotencyAspect.HEADER));
    }

    private ResultActions report(Cookie cookie, String json) throws Exception {
        return mvc.perform(post("/api/v1/reports").cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String targetType, long targetId, String reason) {
        return "{\"targetType\":\"" + targetType + "\",\"targetId\":" + targetId + ",\"reason\":\"" + reason + "\"}";
    }

}
