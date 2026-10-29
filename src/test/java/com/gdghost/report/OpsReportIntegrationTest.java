package com.gdghost.report;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 신고 목록 · 처리 */
@IntegrationTest
class OpsReportIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember staff;
    Cookie staffLogin;
    TestMember author;
    TestMember reporter;
    long question;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        staffLogin = fixtures.loginCookie(staff);
        author = fixtures.member();
        reporter = fixtures.member();
        question = fixtures.question(author.id(), false);
    }

    @Test
    void 목록은_OPEN이_위로_그_안에서_최신순이고_대상_미리보기를_담는다() throws Exception {
        long answer = fixtures.answer(question, author.id(), false, null);
        jdbc.update("UPDATE answer SET body = ? WHERE id = ?", "가".repeat(60), answer);
        long dismissed = fixtures.report(reporter.id(), "QUESTION", question);
        jdbc.update("UPDATE report SET state = 'DISMISSED' WHERE id = ?", dismissed);
        long older = fixtures.report(reporter.id(), "ANSWER", answer);
        long newer = fixtures.report(fixtures.member().id(), "ANSWER", answer);
        jdbc.update("UPDATE report SET created_at = created_at - INTERVAL 1 HOUR WHERE id = ?", older);

        mvc.perform(get("/api/v1/ops/reports").cookie(staffLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].id").value(newer))
                .andExpect(jsonPath("$.items[1].id").value(older))
                .andExpect(jsonPath("$.items[2].id").value(dismissed))
                .andExpect(jsonPath("$.items[0].targetType").value("ANSWER"))
                .andExpect(jsonPath("$.items[0].targetPreview").value("가".repeat(50) + "…"))
                .andExpect(jsonPath("$.items[0].targetLink").value("/questions/" + question + "#answer-" + answer))
                .andExpect(jsonPath("$.items[0].targetDeletedBy").isEmpty())
                .andExpect(jsonPath("$.items[0].sameTargetCount").value(2))
                .andExpect(jsonPath("$.items[0].state").value("OPEN"))
                .andExpect(jsonPath("$.items[0].handledBy").isEmpty())
                .andExpect(jsonPath("$.items[1].reporter.id").value(reporter.id()))
                .andExpect(jsonPath("$.items[1].reporter.nickname").value(reporter.nickname()))
                .andExpect(jsonPath("$.items[2].targetPreview").isString())
                .andExpect(jsonPath("$.items[2].targetLink").value("/questions/" + question))
                .andExpect(jsonPath("$.items[2].sameTargetCount").value(1));

        mvc.perform(get("/api/v1/ops/reports").param("state", "DISMISSED").cookie(staffLogin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(dismissed));
    }

    @Test
    void 목록에_대상_작성자는_없고_탈퇴한_신고자는_닉네임이_null() throws Exception {
        long anonymous = fixtures.question(author.id(), true);
        TestMember withdrawn = fixtures.withdrawn();
        fixtures.report(withdrawn.id(), "QUESTION", anonymous);

        String body = mvc.perform(get("/api/v1/ops/reports").cookie(staffLogin))
                .andExpect(jsonPath("$.items[0].reporter.id").value(withdrawn.id()))
                .andExpect(jsonPath("$.items[0].reporter.nickname").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(author.nickname()).doesNotContain("\"author");
    }

    @Test
    void HIDE는_STAFF_숨김이고_같은_대상_OPEN_신고를_함께_닫는다() throws Exception {
        long first = fixtures.report(reporter.id(), "QUESTION", question);
        long second = fixtures.report(fixtures.member().id(), "QUESTION", question);
        long dismissed = fixtures.report(fixtures.member().id(), "QUESTION", question);
        jdbc.update("UPDATE report SET state = 'DISMISSED' WHERE id = ?", dismissed);

        handle(first, "HIDE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.state").value("HIDDEN"))
                .andExpect(jsonPath("$.targetDeletedBy").value("STAFF"))
                .andExpect(jsonPath("$.handledBy.id").value(staff.id()))
                .andExpect(jsonPath("$.handledAt").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT deleted_by FROM question WHERE id = ?", String.class, question))
                .isEqualTo("STAFF");
        assertThat(reportState(second)).isEqualTo("HIDDEN");
        assertThat(reportState(dismissed)).isEqualTo("DISMISSED");
        Map<String, Object> audit = lastAudit();
        assertThat(audit.get("action")).isEqualTo("REPORT_HIDE");
        assertThat(audit.get("target_type")).isEqualTo("REPORT");
        assertThat(audit.get("target_id")).isEqualTo(first);
        assertThat(audit.get("target_member_id")).isEqualTo(author.id());
        assertThat((Integer) JsonPath.read((String) audit.get("detail"), "$.closedReports")).isEqualTo(2);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(reporter)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 채택된_익명_답변을_숨기면_채택이_풀리고_되돌려도_다시_걸지_않는다() throws Exception {
        long anonQuestion = fixtures.question(author.id(), true);
        TestMember answerer = fixtures.member();
        long answer = fixtures.answer(anonQuestion, answerer.id(), true, null);
        fixtures.answer(anonQuestion, author.id(), false, answer);
        jdbc.update("UPDATE question SET state = 'ADOPTED', adopted_answer_id = ? WHERE id = ?", answer,
                anonQuestion);
        long report = fixtures.report(reporter.id(), "ANSWER", answer);

        handle(report, "HIDE").andExpect(status().isOk());

        assertThat(questionRow(anonQuestion)).containsEntry("state", "WAITING").containsEntry("adopted_answer_id",
                null);
        assertThat(lastAudit().get("target_member_id")).isNull();
        mvc.perform(get("/api/v1/questions/{id}", anonQuestion).cookie(fixtures.loginCookie(reporter)))
                .andExpect(jsonPath("$.answers[0].deletedBy").value("STAFF"));

        handle(report, "RESTORE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RESTORED"))
                .andExpect(jsonPath("$.targetDeletedBy").isEmpty());

        assertThat(questionRow(anonQuestion)).containsEntry("state", "ANSWERED").containsEntry("adopted_answer_id",
                null);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM answer WHERE id = ?", Boolean.class, answer))
                .isTrue();
        assertThat(lastAudit().get("action")).isEqualTo("REPORT_RESTORE");
    }

    @Test
    void DISMISS는_글을_그대로_두고_같은_대상_OPEN_신고를_닫는다() throws Exception {
        long first = fixtures.report(reporter.id(), "QUESTION", question);
        long second = fixtures.report(fixtures.member().id(), "QUESTION", question);

        handle(first, "DISMISS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DISMISSED"));

        assertThat(reportState(second)).isEqualTo("DISMISSED");
        assertThat(questionRow(question).get("deleted_at")).isNull();
        assertThat(lastAudit().get("action")).isEqualTo("REPORT_DISMISS");
    }

    @Test
    void 이미_처리됐거나_상태가_맞지_않으면_409() throws Exception {
        long report = fixtures.report(reporter.id(), "QUESTION", question);

        handle(report, "RESTORE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
        handle(report, "DISMISS").andExpect(status().isOk());
        handle(report, "DISMISS").andExpect(status().isConflict());
        handle(report, "HIDE").andExpect(status().isConflict());

        assertThat(questionRow(question).get("deleted_at")).isNull();
    }

    @Test
    void 작성자가_먼저_지운_글은_HIDE해도_그대로고_되돌릴_수_없다() throws Exception {
        long report = fixtures.report(reporter.id(), "QUESTION", question);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", question);

        handle(report, "HIDE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HIDDEN"))
                .andExpect(jsonPath("$.targetDeletedBy").value("AUTHOR"));

        handle(report, "RESTORE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
        assertThat(questionRow(question).get("deleted_by")).isEqualTo("AUTHOR");
        assertThat(reportState(report)).isEqualTo("HIDDEN");
    }

    @Test
    void 없는_신고는_404_action이_없으면_400() throws Exception {
        handle(999_999, "HIDE").andExpect(status().isNotFound());

        long report = fixtures.report(reporter.id(), "QUESTION", question);
        mvc.perform(patch("/api/v1/ops/reports/{id}", report).cookie(staffLogin).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("action"));
        handle(report, "DELETE").andExpect(status().isBadRequest());
    }

    @Test
    void 부원은_403() throws Exception {
        long report = fixtures.report(reporter.id(), "QUESTION", question);
        Cookie member = fixtures.loginCookie(reporter);

        mvc.perform(get("/api/v1/ops/reports").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/ops/reports/{id}", report).cookie(member).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions handle(long reportId, String action) throws Exception {
        return mvc.perform(patch("/api/v1/ops/reports/{id}", reportId).cookie(staffLogin).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

    private String reportState(long reportId) {
        return jdbc.queryForObject("SELECT state FROM report WHERE id = ?", String.class, reportId);
    }

    private Map<String, Object> questionRow(long questionId) {
        return jdbc.queryForMap("SELECT state, adopted_answer_id, deleted_at, deleted_by FROM question WHERE id = ?",
                questionId);
    }

    private Map<String, Object> lastAudit() {
        return jdbc.queryForMap("SELECT * FROM ops_audit_log ORDER BY id DESC LIMIT 1");
    }

}
