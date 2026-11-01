package com.gdghost.ops;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class GraduationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StatusNoteReminderJob reminderJob;

    TestMember staff;
    Cookie login;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        login = fixtures.loginCookie(staff);
    }

    @Test
    void 대상은_활동_중인_재학_휴학이고_기수_학번으로_거른다() throws Exception {
        TestMember old = member(3, "20", "LEAVE");
        TestMember recent = member(5, "23", "ENROLLED");
        member(3, "19", "GRADUATED");
        jdbc.update("UPDATE member SET student_no = '18' WHERE id = ?", fixtures.suspended().id());
        fixtures.withdrawn();
        jdbc.update("UPDATE member SET generation = 9 WHERE id = ?", staff.id());

        mvc.perform(get("/api/v1/ops/members/graduation-candidates").cookie(login)
                        .param("generation", "3").param("studentNoMax", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains(intValue(old))))
                .andExpect(jsonPath("$.items[0].name").isNotEmpty())
                .andExpect(jsonPath("$.items[0].nickname").value(old.nickname()))
                .andExpect(jsonPath("$.items[0].generation").value(3))
                .andExpect(jsonPath("$.items[0].studentNo").value("20"))
                .andExpect(jsonPath("$.items[0].status").value("LEAVE"))
                .andExpect(jsonPath("$.items[0].email").doesNotExist());

        // 학번 조건이 없으면 학번이 없는 운영진도 보인다
        mvc.perform(get("/api/v1/ops/members/graduation-candidates").cookie(login))
                .andExpect(jsonPath("$.items[*].id",
                        contains(intValue(old), intValue(recent), intValue(staff))));
        mvc.perform(get("/api/v1/ops/members/graduation-candidates").cookie(login).param("studentNoMax", "21"))
                .andExpect(jsonPath("$.items[*].id", contains(intValue(old))));
    }

    @Test
    void 학번_조건이_두_자리_숫자가_아니면_400() throws Exception {
        mvc.perform(get("/api/v1/ops/members/graduation-candidates").cookie(login).param("studentNoMax", "2021"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 일괄_처리하면_졸업이_되고_기록과_앱_내_알림이_남는다() throws Exception {
        TestMember enrolled = fixtures.member();
        TestMember leave = member(5, "20", "LEAVE");

        graduate(List.of(enrolled.id(), leave.id(), enrolled.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.skippedIds").isEmpty());

        assertThat(statusOf(enrolled)).isEqualTo("GRADUATED");
        assertThat(statusOf(leave)).isEqualTo("GRADUATED");
        assertThat(jdbc.queryForList("SELECT target_member_id FROM ops_audit_log WHERE actor_id = ? AND action = 'GRADUATE'",
                Long.class, staff.id())).containsExactlyInAnyOrder(enrolled.id(), leave.id());
        assertThat(jdbc.queryForMap("""
                SELECT title, body, target_type, target_id FROM notification
                WHERE member_id = ? AND type = 'GRADUATED'
                """, leave.id()))
                .containsEntry("title", "졸업을 축하해요")
                .containsEntry("body", "근황을 업데이트하면 맞는 질문이 와요")
                .containsEntry("target_type", "MEMBER")
                .containsEntry("target_id", leave.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'GRADUATED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void 졸업_탈퇴_정지_없는_부원은_건너뛰고_다시_불러도_겹치지_않는다() throws Exception {
        TestMember member = fixtures.member();
        TestMember graduated = member(5, "20", "GRADUATED");
        TestMember withdrawn = fixtures.withdrawn();
        TestMember suspended = fixtures.suspended();

        graduate(List.of(member.id(), graduated.id(), withdrawn.id(), suspended.id(), 999_999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.skippedIds", containsInAnyOrder(intValue(graduated), intValue(withdrawn),
                        intValue(suspended), 999_999)));
        graduate(List.of(member.id()))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.skippedIds", contains(intValue(member))));

        assertThat(statusOf(suspended)).isEqualTo("ENROLLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'GRADUATED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void 인원이_0명이거나_200명을_넘으면_400() throws Exception {
        graduate(List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        graduate(Collections.nCopies(201, 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 근황_알림_배치는_활동_중인_전_부원에게_자기_프로필로_보낸다() {
        TestMember graduated = member(5, "20", "GRADUATED");
        fixtures.suspended();
        fixtures.withdrawn();

        reminderJob.run();

        assertThat(jdbc.queryForList("SELECT member_id FROM notification WHERE type = 'STATUS_NOTE_REMINDER'",
                Long.class)).containsExactlyInAnyOrder(staff.id(), graduated.id());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification WHERE target_type = 'MEMBER' AND target_id = member_id
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT state, message FROM batch_log WHERE job_name = 'status_note_reminder'"))
                .containsEntry("state", "SUCCESS")
                .containsEntry("message", "notified=2");
    }

    @Test
    void 근황_알림_배치는_3월과_9월_1일_09시에_돈다() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        CronExpression cron = CronExpression.parse(StatusNoteReminderJob.CRON);

        ZonedDateTime september = cron.next(ZonedDateTime.of(2027, 3, 1, 9, 0, 0, 0, kst));
        assertThat(september).isEqualTo(ZonedDateTime.of(2027, 9, 1, 9, 0, 0, 0, kst));
        assertThat(cron.next(september)).isEqualTo(ZonedDateTime.of(2028, 3, 1, 9, 0, 0, 0, kst));
    }

    private TestMember member(int generation, String studentNo, String memberStatus) {
        TestMember member = fixtures.member();
        jdbc.update("UPDATE member SET generation = ?, student_no = ?, status = ? WHERE id = ?", generation, studentNo,
                memberStatus, member.id());
        return member;
    }

    private String statusOf(TestMember member) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, member.id());
    }

    private static int intValue(TestMember member) {
        return (int) member.id();
    }

    private ResultActions graduate(List<Long> memberIds) throws Exception {
        String ids = memberIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(post("/api/v1/ops/members/graduations").cookie(login).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberIds\":[" + ids + "]}"));
    }

}
