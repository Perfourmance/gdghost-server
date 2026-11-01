package com.gdghost.ops;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

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
class OpsDashboardIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    TestMember staff;
    TestMember member;
    Cookie login;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        member = fixtures.member();
        login = fixtures.loginCookie(staff);
    }

    @Test
    void 데이터가_없으면_0과_빈_목록() throws Exception {
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thisWeek.questionCount").value(0))
                .andExpect(jsonPath("$.thisWeek.unansweredCount").value(0))
                .andExpect(jsonPath("$.thisWeek.routingConversionRate").value(0.0))
                .andExpect(jsonPath("$.staleQuestions.total").value(0))
                .andExpect(jsonPath("$.staleQuestions.items").isEmpty())
                .andExpect(jsonPath("$.openReports.total").value(0))
                .andExpect(jsonPath("$.pendingCertifications").value(0))
                .andExpect(jsonPath("$.unconfirmedMustRead").isEmpty());
    }

    @Test
    void 이번_주는_지난_주_질문과_지운_질문을_세지_않는다() throws Exception {
        fixtures.question(member.id(), false);
        long answered = fixtures.question(member.id(), false);
        jdbc.update("UPDATE question SET state = 'ANSWERED' WHERE id = ?", answered);
        long deleted = fixtures.question(member.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
        age(fixtures.question(member.id(), false), 8);

        dashboard()
                .andExpect(jsonPath("$.thisWeek.questionCount").value(2))
                .andExpect(jsonPath("$.thisWeek.unansweredCount").value(1));
    }

    @Test
    void 전환율은_이번_주에_보낸_부원_알림_기준이다() throws Exception {
        long question = fixtures.question(member.id(), false);
        routing(question, "MEMBER", "ANSWERED", 0);
        routing(question, "DIRECT", "SENT", 0);
        routing(question, "STAFF", "ANSWERED", 0);
        routing(question, "MEMBER", "ANSWERED", 8);

        dashboard().andExpect(jsonPath("$.thisWeek.routingConversionRate").value(0.5));
    }

    @Test
    void 삼일_미답변은_전체_수와_오래된_5건이고_익명_작성자는_숨긴다() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long id = fixtures.question(member.id(), i == 0);
            age(id, 20 - i);
            ids.add(id);
        }
        fixtures.question(member.id(), false);

        dashboard()
                .andExpect(jsonPath("$.staleQuestions.total").value(6))
                .andExpect(jsonPath("$.staleQuestions.items", hasSize(5)))
                .andExpect(jsonPath("$.staleQuestions.items[0].id").value(ids.getFirst()))
                .andExpect(jsonPath("$.staleQuestions.items[0].author.anonymous").value(true))
                .andExpect(jsonPath("$.staleQuestions.items[0].author.id").doesNotExist())
                .andExpect(jsonPath("$.staleQuestions.items[4].id").value(ids.get(4)));
    }

    @Test
    void 신고는_미처리만_세고_상위_5건이다() throws Exception {
        for (int i = 0; i < 6; i++) {
            fixtures.report(member.id(), "QUESTION", fixtures.question(staff.id(), false));
        }
        long handled = fixtures.report(member.id(), "QUESTION", fixtures.question(staff.id(), false));
        jdbc.update("UPDATE report SET state = 'DISMISSED' WHERE id = ?", handled);

        dashboard()
                .andExpect(jsonPath("$.openReports.total").value(6))
                .andExpect(jsonPath("$.openReports.items", hasSize(5)))
                .andExpect(jsonPath("$.openReports.items[0].state").value("OPEN"));
    }

    @Test
    void 인증_대기는_탈퇴한_부원을_뺀다() throws Exception {
        long event = event();
        certification(event, member.id(), "PENDING");
        certification(event, staff.id(), "APPROVED");
        certification(event, fixtures.withdrawn().id(), "PENDING");

        dashboard().andExpect(jsonPath("$.pendingCertifications").value(1));
    }

    @Test
    void 미확인_필독은_마감이_지나지_않고_아직_다_확인하지_않은_공지만() throws Exception {
        long open = mustRead("NULL");
        confirm(open, member.id());
        long allConfirmed = mustRead("NULL");
        confirm(allConfirmed, member.id());
        confirm(allConfirmed, staff.id());
        mustRead("'2020-01-01'");
        fixtures.notice(staff.id());
        fixtures.withdrawn();

        dashboard()
                .andExpect(jsonPath("$.unconfirmedMustRead[*].noticeId", contains((int) open)))
                .andExpect(jsonPath("$.unconfirmedMustRead[0].title").isNotEmpty())
                .andExpect(jsonPath("$.unconfirmedMustRead[0].confirmedCount").value(1))
                .andExpect(jsonPath("$.unconfirmedMustRead[0].memberCount").value(2));
    }

    private long mustRead(String dueDate) {
        long notice = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET is_must_read = TRUE, due_date = " + dueDate + " WHERE id = ?", notice);
        return notice;
    }

    private void confirm(long notice, long memberId) {
        jdbc.update("""
                INSERT INTO notice_read (notice_id, member_id, read_at, confirmed_at)
                VALUES (?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, notice, memberId);
    }

    private void age(long question, int days) {
        jdbc.update("UPDATE question SET created_at = created_at - INTERVAL ? DAY WHERE id = ?", days, question);
    }

    private void routing(long question, String stage, String state, int daysAgo) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6) - INTERVAL ? DAY)
                """, question, staff.id(), stage, state, daysAgo);
    }

    private long event() {
        jdbc.update("""
                INSERT INTO event (created_by, name, start_date, end_date, description, requirement, guide_steps)
                VALUES (?, '이벤트', '2026-01-01', '2030-01-01', '설명', '사진', '[]')
                """, staff.id());
        return jdbc.queryForObject("SELECT MAX(id) FROM event", Long.class);
    }

    private void certification(long event, long memberId, String state) {
        jdbc.update("INSERT INTO certification (event_id, member_id, body, state) VALUES (?, ?, '인증', ?)", event,
                memberId, state);
    }

    private ResultActions dashboard() throws Exception {
        return mvc.perform(get("/api/v1/ops/dashboard").cookie(login));
    }

}
