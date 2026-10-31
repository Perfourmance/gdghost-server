package com.gdghost.routing;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

@IntegrationTest
class OpsRoutingIntegrationTest {

    private static final String SCORE = """
            {"version": "%s", "rule": 0.6, "profile": 0.4, "history": 0, "activity": 0, "total": 0.36,
             "reasons": ["경험: 인턴"]}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie staff;
    private TestMember author;
    private TestMember member;

    @BeforeEach
    void setUp() {
        staff = fixtures.loginCookie(fixtures.staff());
        author = fixtures.member();
        member = fixtures.member();
    }

    @Test
    void 삼일_넘은_대기_질문을_오래된_순으로_보여_주고_익명_작성자는_숨긴다() throws Exception {
        long newer = question(true, 4);
        long older = question(false, 6);
        question(false, 2);
        long answered = question(false, 9);
        jdbc.update("UPDATE question SET state = 'ANSWERED' WHERE id = ?", answered);
        long deleted = question(false, 9);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
        routing(older, member.id(), "MEMBER", "PASSED", null, now());
        routing(older, fixtures.member().id(), "MEMBER", "SENT", null, now());
        routing(older, fixtures.member().id(), "STAFF", "SENT", null, now());

        get("/api/v1/ops/questions/stale").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(older))
                .andExpect(jsonPath("$.items[0].waitingDays").value(6))
                .andExpect(jsonPath("$.items[0].sentCount").value(2))
                .andExpect(jsonPath("$.items[0].passedCount").value(1))
                .andExpect(jsonPath("$.items[0].author.nickname").value(author.nickname()))
                .andExpect(jsonPath("$.items[1].id").value(newer))
                .andExpect(jsonPath("$.items[1].author.anonymous").value(true))
                .andExpect(jsonPath("$.items[1].author.label").value("익명"))
                .andExpect(jsonPath("$.items[1].author.id").doesNotExist())
                .andExpect(jsonPath("$.items[1].author.nickname").doesNotExist());
    }

    @Test
    void 발송_이력은_최신순이고_운영진_단계와_보내지_않은_지목을_뺀다() throws Exception {
        long question = question(true, 1);
        routing(question, member.id(), "MEMBER", "ANSWERED", "2026-10-v1", now());
        long passed = routing(question, fixtures.member().id(), "MEMBER", "PASSED", "2026-10-v1", now());
        routing(question, fixtures.member().id(), "STAFF", "SENT", null, now());
        jdbc.update("INSERT INTO routing (question_id, member_id, stage, state) VALUES (?, ?, 'DIRECT', 'CANDIDATE')",
                question, fixtures.member().id());

        get("/api/v1/ops/routing/logs").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(passed))
                .andExpect(jsonPath("$.items[1].member.id").value(member.id()))
                .andExpect(jsonPath("$.items[1].member.nickname").value(member.nickname()))
                .andExpect(jsonPath("$.items[1].questionId").value(question))
                .andExpect(jsonPath("$.items[1].rankNo").value(1))
                .andExpect(jsonPath("$.items[1].scoreDetail.version").value("2026-10-v1"))
                .andExpect(jsonPath("$.items[1].scoreDetail.reasons[0]").value("경험: 인턴"))
                .andExpect(jsonPath("$.items[1].author").doesNotExist());
    }

    @Test
    void 발송_이력을_상태와_부원으로_거른다() throws Exception {
        long question = question(false, 1);
        routing(question, member.id(), "MEMBER", "ANSWERED", "2026-10-v1", now());
        routing(question, fixtures.member().id(), "MEMBER", "PASSED", "2026-10-v1", now());

        get("/api/v1/ops/routing/logs?state=PASSED").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].state").value("PASSED"));
        get("/api/v1/ops/routing/logs?memberId=" + member.id()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].member.id").value(member.id()));
        get("/api/v1/ops/routing/logs?state=CANDIDATE").andExpect(status().isBadRequest());
    }

    @Test
    void 지목은_점수와_순위가_없고_탈퇴자는_닉네임이_없다() throws Exception {
        long question = question(false, 1);
        routing(question, fixtures.withdrawn().id(), "DIRECT", "SENT", null, now());

        get("/api/v1/ops/routing/logs").andExpect(jsonPath("$.items[0].rankNo").isEmpty())
                .andExpect(jsonPath("$.items[0].scoreDetail").isEmpty())
                .andExpect(jsonPath("$.items[0].member.nickname").isEmpty());
    }

    @Test
    void 집계는_부원_지목_대비_비율이고_운영진은_따로_세며_버전별로_나눈다() throws Exception {
        long question = question(false, 1);
        routing(question, member.id(), "MEMBER", "ANSWERED", "v1", now());
        routing(question, fixtures.member().id(), "MEMBER", "PASSED", "v1", now());
        routing(question, fixtures.member().id(), "MEMBER", "SENT", "v2", now());
        routing(question, fixtures.member().id(), "DIRECT", "ANSWERED", null, now());
        routing(question, fixtures.member().id(), "STAFF", "SENT", null, now());
        jdbc.update("INSERT INTO routing (question_id, member_id, stage, state) VALUES (?, ?, 'DIRECT', 'CANDIDATE')",
                question, fixtures.member().id());
        for (String state : new String[] {"SUCCESS", "SUCCESS", "SUCCESS", "FALLBACK"}) {
            jdbc.update("INSERT INTO ai_log (kind, state, created_at) VALUES ('EMBED_QUESTION', ?, ?)", state, now());
        }

        get("/api/v1/ops/routing/stats").andExpect(status().isOk())
                .andExpect(jsonPath("$.sentCount").value(4))
                .andExpect(jsonPath("$.answeredCount").value(2))
                .andExpect(jsonPath("$.passedCount").value(1))
                .andExpect(jsonPath("$.staffFirstCount").value(1))
                .andExpect(jsonPath("$.conversionRate").value(closeTo(0.5, 0.0001)))
                .andExpect(jsonPath("$.passRate").value(closeTo(0.25, 0.0001)))
                .andExpect(jsonPath("$.fallbackRate").value(closeTo(0.25, 0.0001)))
                .andExpect(jsonPath("$.byVersion", hasSize(2)))
                .andExpect(jsonPath("$.byVersion[0].version").value("v1"))
                .andExpect(jsonPath("$.byVersion[0].sentCount").value(2))
                .andExpect(jsonPath("$.byVersion[0].conversionRate").value(closeTo(0.5, 0.0001)))
                .andExpect(jsonPath("$.byVersion[1].version").value("v2"));
    }

    @Test
    void 집계_기간은_한국_날짜_기준이고_데이터가_없으면_0이다() throws Exception {
        long question = question(false, 1);
        // 10월 30일 15:00 UTC = 한국 10월 31일 00:00
        routing(question, member.id(), "MEMBER", "SENT", "v1", LocalDateTime.parse("2026-10-30T15:00:00"));

        get("/api/v1/ops/routing/stats?from=2026-10-31&to=2026-10-31").andExpect(jsonPath("$.sentCount").value(1));
        get("/api/v1/ops/routing/stats?from=2026-10-30&to=2026-10-30").andExpect(jsonPath("$.sentCount").value(0))
                .andExpect(jsonPath("$.conversionRate").value(0.0))
                .andExpect(jsonPath("$.fallbackRate").value(0.0))
                .andExpect(jsonPath("$.byVersion").isEmpty());
    }

    @Test
    void 기간이_거꾸로거나_1년을_넘으면_400이다() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 31);
        get("/api/v1/ops/routing/stats?from=2026-10-31&to=2026-10-30").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        get("/api/v1/ops/routing/stats?from=" + today.minusYears(1).minusDays(1) + "&to=" + today)
                .andExpect(status().isBadRequest());
        get("/api/v1/ops/routing/stats?from=" + today.minusYears(1) + "&to=" + today).andExpect(status().isOk());
        get("/api/v1/ops/routing/stats?from=날짜아님").andExpect(status().isBadRequest());
    }

    private ResultActions get(String url) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).cookie(staff));
    }

    private long question(boolean anonymous, int daysAgo) {
        long question = fixtures.question(author.id(), anonymous);
        jdbc.update("UPDATE question SET created_at = ? WHERE id = ?", now().minusDays(daysAgo).minusMinutes(1),
                question);
        return question;
    }

    private long routing(long question, long memberId, String stage, String state, String version, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, rank_no, score_detail, state, sent_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, question, memberId, stage, version == null ? null : 1,
                version == null ? null : SCORE.formatted(version), state, at);
        return jdbc.queryForObject("SELECT MAX(id) FROM routing", Long.class);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

}
