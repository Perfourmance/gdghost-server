package com.gdghost.admin;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.gdghost.support.Fixtures;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class AdminLogIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    Cookie login;

    @BeforeEach
    void setUp() {
        login = fixtures.loginCookie(fixtures.dev());
    }

    @Test
    void 배치_로그는_최신순이고_걸린_시간을_준다() throws Exception {
        long first = batch("routing_10min", "SUCCESS", 1);
        long second = batch("cleanup_nightly", "FAILED", 1);

        mvc.perform(get("/api/v1/admin/batches").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) second, (int) first)))
                .andExpect(jsonPath("$.items[0].jobName").value("cleanup_nightly"))
                .andExpect(jsonPath("$.items[0].state").value("FAILED"))
                .andExpect(jsonPath("$.items[0].message").value("메시지"))
                .andExpect(jsonPath("$.items[0].durationMs").value(412))
                .andExpect(jsonPath("$.items[0].startedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].endedAt").isNotEmpty());
    }

    @Test
    void 끝나지_않은_배치는_걸린_시간이_없다() throws Exception {
        jdbc.update("INSERT INTO batch_log (job_name, state, started_at) VALUES ('x', 'FAILED', UTC_TIMESTAMP(6))");

        mvc.perform(get("/api/v1/admin/batches").cookie(login))
                .andExpect(jsonPath("$.items[0].endedAt").isEmpty())
                .andExpect(jsonPath("$.items[0].durationMs").isEmpty());
    }

    @Test
    void 배치_로그를_이름과_상태로_거르고_없는_이름은_빈_목록이다() throws Exception {
        long match = batch("routing_10min", "FAILED", 1);
        batch("routing_10min", "SUCCESS", 1);
        batch("cleanup_nightly", "FAILED", 1);

        mvc.perform(get("/api/v1/admin/batches").param("jobName", "routing_10min").param("state", "FAILED")
                .cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) match)));
        mvc.perform(get("/api/v1/admin/batches").param("jobName", "없는_배치").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 최근_24시간_실패_수는_필터와_상관없이_센다() throws Exception {
        batch("a", "FAILED", 1);
        batch("b", "FAILED", 23);
        batch("c", "FAILED", 25);
        batch("d", "SUCCESS", 1);

        mvc.perform(get("/api/v1/admin/batches").param("jobName", "d").cookie(login))
                .andExpect(jsonPath("$.summary.last24hFailed").value(2));
    }

    @Test
    void 배치_로그를_커서로_이어_본다() throws Exception {
        long first = batch("a", "SUCCESS", 1);
        long second = batch("a", "SUCCESS", 1);

        String body = mvc.perform(get("/api/v1/admin/batches").param("size", "1").cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) second)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/admin/batches").param("cursor", JsonPath.<String>read(body, "$.nextCursor"))
                .cookie(login))
                .andExpect(jsonPath("$.items[*].id", contains((int) first)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void AI_로그를_종류와_상태로_거르고_보낸_글은_없다() throws Exception {
        long match = ai("EMBED_QUESTION", "SUCCESS", 310, 0);
        ai("EMBED_QUESTION", "FAILED", null, 0);
        ai("FIND_SENIOR", "SUCCESS", 100, 0);

        mvc.perform(get("/api/v1/admin/ai-logs").param("kind", "EMBED_QUESTION").param("state", "SUCCESS")
                .cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) match)))
                .andExpect(jsonPath("$.items[0].kind").value("EMBED_QUESTION"))
                .andExpect(jsonPath("$.items[0].targetId").value(88))
                .andExpect(jsonPath("$.items[0].model").value("@cf/baai/bge-m3"))
                .andExpect(jsonPath("$.items[0].latencyMs").value(310))
                .andExpect(jsonPath("$.items[0].state").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void AI_요약은_최근_7일_전체_기준이다() throws Exception {
        for (int i = 1; i <= 18; i++) {
            ai("EMBED_QUESTION", "SUCCESS", i * 10, 0);
        }
        ai("EMBED_QUESTION", "FALLBACK", null, 0);
        ai("FIND_SENIOR", "FAILED", 9999, 0);
        ai("EMBED_QUESTION", "SUCCESS", 99999, 8);

        mvc.perform(get("/api/v1/admin/ai-logs").param("kind", "FIND_SENIOR").cookie(login))
                .andExpect(jsonPath("$.summary.last7d.total").value(20))
                .andExpect(jsonPath("$.summary.last7d.successRate", closeTo(0.9, 0.0001)))
                .andExpect(jsonPath("$.summary.last7d.fallbackRate", closeTo(0.05, 0.0001)))
                .andExpect(jsonPath("$.summary.last7d.p95LatencyMs").value(180));
    }

    @Test
    void AI_기록이_없으면_요약은_0과_null이다() throws Exception {
        mvc.perform(get("/api/v1/admin/ai-logs").cookie(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.last7d.total").value(0))
                .andExpect(jsonPath("$.summary.last7d.successRate").value(0.0))
                .andExpect(jsonPath("$.summary.last7d.fallbackRate").value(0.0))
                .andExpect(jsonPath("$.summary.last7d.p95LatencyMs").isEmpty())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 잘못된_입력은_400() throws Exception {
        mvc.perform(get("/api/v1/admin/batches").param("state", "RUNNING").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/admin/ai-logs").param("kind", "UNKNOWN").cookie(login))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/ai-logs").param("state", "RUNNING").cookie(login))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/batches").param("cursor", "aGVsbG8").cookie(login))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    private long batch(String jobName, String state, int hoursAgo) {
        jdbc.update("""
                INSERT INTO batch_log (job_name, state, message, started_at, ended_at)
                VALUES (?, ?, '메시지', @at := UTC_TIMESTAMP(3) - INTERVAL ? HOUR, @at + INTERVAL 412000 MICROSECOND)
                """, jobName, state, hoursAgo);
        return jdbc.queryForObject("SELECT MAX(id) FROM batch_log", Long.class);
    }

    private long ai(String kind, String state, Integer latencyMs, int daysAgo) {
        jdbc.update("""
                INSERT INTO ai_log (kind, target_id, model, latency_ms, state, created_at)
                VALUES (?, 88, '@cf/baai/bge-m3', ?, ?, UTC_TIMESTAMP(6) - INTERVAL ? DAY)
                """, kind, latencyMs, state, daysAgo);
        return jdbc.queryForObject("SELECT MAX(id) FROM ai_log", Long.class);
    }

}
