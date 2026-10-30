package com.gdghost.report;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 공지 댓글 신고 · 숨김 · 되돌리기 */
@IntegrationTest
class NoticeCommentReportIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeDiscordGateway discord;

    TestMember staff;
    Cookie staffLogin;
    TestMember author;
    Cookie reporterLogin;
    long notice;
    long comment;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        staffLogin = fixtures.loginCookie(staff);
        author = fixtures.member();
        reporterLogin = fixtures.loginCookie(fixtures.member());
        notice = fixtures.notice(staff.id());
        comment = fixtures.noticeComment(notice, author.id());
        discord.clear();
    }

    @Test
    void 공지_댓글을_신고하면_201이고_운영진_디스코드에_댓글_링크를_보낸다() throws Exception {
        report(comment).andExpect(status().isCreated());

        discord.await(sent -> sent.channel() == DiscordChannel.STAFF
                && sent.message().title().equals("신고 접수 · 공지 댓글")
                && sent.message().url().endsWith("/notices/" + notice + "#comment-" + comment));
    }

    @Test
    void 지운_댓글이나_공지가_지워진_댓글은_신고할_수_없다() throws Exception {
        long removed = fixtures.noticeComment(notice, author.id());
        jdbc.update("UPDATE notice_comment SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?",
                removed);
        long otherNotice = fixtures.notice(staff.id());
        long orphan = fixtures.noticeComment(otherNotice, author.id());
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", otherNotice);

        report(removed).andExpect(status().isNotFound());
        report(orphan).andExpect(status().isNotFound());
        report(comment + 1000).andExpect(status().isNotFound());
    }

    @Test
    void 숨기면_상세에서_빠지고_되돌리면_다시_보이며_운영_기록에_작성자가_남는다() throws Exception {
        long reportId = JsonPath.<Number>read(report(comment).andReturn().getResponse().getContentAsString(), "$.id")
                .longValue();

        mvc.perform(get("/api/v1/ops/reports").cookie(staffLogin))
                .andExpect(jsonPath("$.items[0].targetType").value("NOTICE_COMMENT"))
                .andExpect(jsonPath("$.items[0].targetLink").value("/notices/" + notice + "#comment-" + comment))
                .andExpect(jsonPath("$.items[0].targetPreview").exists());

        handle(reportId, "HIDE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HIDDEN"))
                .andExpect(jsonPath("$.targetDeletedBy").value("STAFF"));
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(staffLogin))
                .andExpect(jsonPath("$.comments").isEmpty());
        assertThat(jdbc.queryForObject("SELECT target_member_id FROM ops_audit_log ORDER BY id DESC LIMIT 1",
                Long.class)).isEqualTo(author.id());

        handle(reportId, "RESTORE").andExpect(jsonPath("$.state").value("RESTORED"));
        mvc.perform(get("/api/v1/notices/{id}", notice).cookie(staffLogin))
                .andExpect(jsonPath("$.comments[0].id").value(comment));
    }

    @Test
    void 숨긴_댓글은_작성자도_지울_수_없다() throws Exception {
        long reportId = JsonPath.<Number>read(report(comment).andReturn().getResponse().getContentAsString(), "$.id")
                .longValue();
        handle(reportId, "HIDE").andExpect(status().isOk());

        mvc.perform(delete("/api/v1/notice-comments/{id}", comment).cookie(fixtures.loginCookie(author))
                        .with(csrfToken()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM notice_comment WHERE id = ?", String.class, comment))
                .isEqualTo("STAFF");
    }

    @Test
    void 작성자가_먼저_지운_댓글은_숨겨도_그대로이고_되돌리기는_409() throws Exception {
        long reportId = JsonPath.<Number>read(report(comment).andReturn().getResponse().getContentAsString(), "$.id")
                .longValue();
        jdbc.update("UPDATE notice_comment SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?",
                comment);

        handle(reportId, "HIDE").andExpect(jsonPath("$.targetDeletedBy").value("AUTHOR"));
        handle(reportId, "RESTORE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
    }

    private ResultActions report(long commentId) throws Exception {
        return mvc.perform(post("/api/v1/reports").cookie(reporterLogin).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"NOTICE_COMMENT\",\"targetId\":" + commentId + ",\"reason\":\"욕설\"}"));
    }

    private ResultActions handle(long reportId, String action) throws Exception {
        return mvc.perform(patch("/api/v1/ops/reports/{id}", reportId).cookie(staffLogin).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
    }

}
