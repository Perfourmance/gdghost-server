package com.gdghost.admin;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.common.error.ErrorLogRecorder;
import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class AdminErrorLogIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ErrorLogRecorder errorLog;

    Cookie login;

    @BeforeEach
    void setUp() {
        login = fixtures.loginCookie(fixtures.dev());
    }

    @Test
    void 마지막_발생이_최근인_순이고_목록에는_스택이_없다() throws Exception {
        long old = error("오래된 에러", "GET /a", "2026-10-01 00:00:00");
        long recent = error("최근 에러", "POST /b", "2026-10-20 00:00:00");

        mvc.perform(get("/api/v1/admin/errors").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) recent, (int) old)))
                .andExpect(jsonPath("$.items[0].message").value("최근 에러"))
                .andExpect(jsonPath("$.items[0].path").value("POST /b"))
                .andExpect(jsonPath("$.items[0].count").value(3))
                .andExpect(jsonPath("$.items[0].fingerprint").isNotEmpty())
                .andExpect(jsonPath("$.items[0].firstAt").value("2026-09-01T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].lastAt").value("2026-10-20T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].stack").doesNotExist());
    }

    @Test
    void 커서로_이어_보고_같은_시각도_빠지지_않는다() throws Exception {
        long first = error("하나", "GET /a", "2026-10-01 00:00:00");
        long second = error("둘", "GET /a", "2026-10-01 00:00:00");
        long third = error("셋", "GET /a", "2026-09-01 00:00:00");

        String body = mvc.perform(get("/api/v1/admin/errors").param("size", "1").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) second)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.nextCursor");

        mvc.perform(get("/api/v1/admin/errors").param("cursor", cursor).cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) first, (int) third)))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 메시지와_경로로_찾고_특수문자는_글자_그대로_찾는다() throws Exception {
        long byMessage = error("NullPointer 100%", "GET /a", "2026-10-03 00:00:00");
        long byPath = error("다른 에러", "POST /api/nullpointer", "2026-10-02 00:00:00");
        error("100원", "GET /c", "2026-10-01 00:00:00");

        mvc.perform(get("/api/v1/admin/errors").param("q", "nullpointer").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) byMessage, (int) byPath)));
        mvc.perform(get("/api/v1/admin/errors").param("q", "100%").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) byMessage)));
    }

    @Test
    void since_이후에_발생한_것만() throws Exception {
        long recent = error("최근", "GET /a", "2026-10-20 00:00:00");
        error("오래됨", "GET /a", "2026-10-01 00:00:00");

        mvc.perform(get("/api/v1/admin/errors").param("since", "2026-10-20T00:00:00Z").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) recent)));
    }

    @Test
    void 잘못된_입력은_400() throws Exception {
        mvc.perform(get("/api/v1/admin/errors").param("since", "어제").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/admin/errors").param("cursor", "aGVsbG8").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
        mvc.perform(get("/api/v1/admin/errors").param("size", "51").cookie(login))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_스택까지_주고_없으면_404() throws Exception {
        long id = error("에러", "GET /a", "2026-10-01 00:00:00");

        mvc.perform(get("/api/v1/admin/errors/{id}", id).cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.stack").value("스택 전체"));
        mvc.perform(get("/api/v1/admin/errors/{id}", id + 1000).cookie(login))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 메시지와_스택의_개인정보는_저장_전에_가린다() throws Exception {
        errorLog.recordQuietly(new IllegalStateException("hong.gil+1@gmail.com password=abc123 token: xyz"), "GET /a");
        long id = jdbc.queryForObject("SELECT id FROM error_log", Long.class);

        mvc.perform(get("/api/v1/admin/errors/{id}", id).cookie(login))
                .andExpect(jsonPath("$.message", containsString("[email] password=[hidden] token: [hidden]")))
                .andExpect(jsonPath("$.stack", containsString("[email]")))
                .andExpect(jsonPath("$.stack", not(containsString("gmail.com"))))
                .andExpect(jsonPath("$.stack", not(containsString("abc123"))));
    }

    private long error(String message, String path, String lastAt) {
        jdbc.update("""
                INSERT INTO error_log (fingerprint, message, stack, path, count, first_at, last_at)
                VALUES (SHA2(UUID(), 256), ?, '스택 전체', ?, 3, '2026-09-01 00:00:00', ?)
                """, message, path, lastAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM error_log", Long.class);
    }

}
