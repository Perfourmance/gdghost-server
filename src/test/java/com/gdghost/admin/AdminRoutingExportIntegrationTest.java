package com.gdghost.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class AdminRoutingExportIntegrationTest {

    private static final String HEADER = "routing_id,question_id,question_category,member_id,stage,rank_no,state,"
            + "sent_at,answered_at,version,rule,profile,history,activity,total";
    private static final String DETAIL = """
            {"version":"2026-10-v1","rule":0.6,"profile":0.42,"history":0.71,"activity":0.08,"total":0.62,"reasons":["경험: 인턴"]}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeDiscordGateway discord;

    TestMember dev;
    TestMember member;
    Cookie login;

    @BeforeEach
    void setUp() {
        dev = fixtures.dev();
        member = fixtures.member();
        login = fixtures.loginCookie(dev);
        discord.clear();
    }

    @Test
    void 파일_이름과_형식을_정하고_점수와_결과를_넣는다() throws Exception {
        long question = fixtures.question(member.id(), false);
        long routing = routing(question, "MEMBER", 1, "ANSWERED", "2026-10-04 12:00:00", "2026-10-04 13:10:00", DETAIL);

        var response = mvc.perform(export("2026-10-01", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"routing_2026-10-01_2026-10-31.csv\""))
                .andReturn().getResponse();

        assertThat(response.getContentAsString(StandardCharsets.UTF_8).split("\n")).containsExactly(HEADER,
                routing + "," + question + ",JOB," + member.id()
                        + ",MEMBER,1,ANSWERED,2026-10-04T12:00:00Z,2026-10-04T13:10:00Z,2026-10-v1,0.6,0.42,0.71,0.08,0.62");
    }

    @Test
    void 익명_질문은_받은_사람을_비우고_이름과_제목은_어디에도_없다() throws Exception {
        long question = fixtures.question(member.id(), true);
        routing(question, "MEMBER", 1, "ANSWERED", "2026-10-04 12:00:00", "2026-10-04 13:00:00", DETAIL);
        String title = jdbc.queryForObject("SELECT title FROM question WHERE id = ?", String.class, question);
        String nickname = jdbc.queryForObject("SELECT nickname FROM member WHERE id = ?", String.class, member.id());

        String csv = csv("2026-10-01", "2026-10-31");

        assertThat(csv.split("\n")[1].split(",", -1)[3]).isEmpty();
        assertThat(csv).doesNotContain(title, nickname, "경험: 인턴");
    }

    @Test
    void 점수가_없는_운영진_지목과_결과_없는_행은_빈칸이다() throws Exception {
        long question = fixtures.question(member.id(), false);
        routing(question, "STAFF", null, "SENT", "2026-10-04 12:00:00", null, null);
        routing(question, "DIRECT", null, "PASSED", "2026-10-04 13:00:00", "2026-10-04 14:00:00", null);

        List<String> lines = List.of(csv("2026-10-01", "2026-10-31").split("\n"));

        assertThat(lines.get(1)).endsWith(",STAFF,,SENT,2026-10-04T12:00:00Z,,,,,,,");
        // 넘긴 시각은 answered_at이 아니다
        assertThat(lines.get(2)).endsWith(",DIRECT,,PASSED,2026-10-04T13:00:00Z,,,,,,,");
    }

    @Test
    void 기간은_보낸_시각의_한국_날짜_기준이고_보내지_않은_행은_뺀다() throws Exception {
        long question = fixtures.question(member.id(), false);
        routing(question, "MEMBER", 1, "SENT", "2026-09-30 14:59:59", null, DETAIL);
        long first = routing(question, "MEMBER", 1, "SENT", "2026-09-30 15:00:00", null, DETAIL);
        long last = routing(question, "MEMBER", 1, "SENT", "2026-10-31 14:59:59", null, DETAIL);
        routing(question, "MEMBER", 1, "SENT", "2026-10-31 15:00:00", null, DETAIL);
        routing(question, "DIRECT", null, "CANCELED", null, null, null);

        List<String> lines = List.of(csv("2026-10-01", "2026-10-31").split("\n"));

        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).startsWith(first + ",");
        assertThat(lines.get(2)).startsWith(last + ",");
    }

    @Test
    void 데이터가_없으면_머리글만_준다() throws Exception {
        assertThat(csv("2026-10-01", "2026-10-31")).isEqualTo(HEADER + "\n");
    }

    @Test
    void 요청마다_운영_기록과_개발자_디스코드에_남는다() throws Exception {
        long question = fixtures.question(member.id(), false);
        routing(question, "MEMBER", 1, "SENT", "2026-10-04 12:00:00", null, DETAIL);

        csv("2026-10-01", "2026-10-31");
        csv("2026-10-01", "2026-10-31");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ops_audit_log WHERE action = 'ROUTING_EXPORT' AND actor_id = ?
                """, Integer.class, dev.id())).isEqualTo(2);
        assertThat(discord.await(s -> s.is(DiscordChannel.DEV, "라우팅 CSV 내보내기"), 2).getFirst().message().description())
                .contains("2026-10-01 ~ 2026-10-31", "1행");
    }

    @Test
    void 기간이_잘못되면_400이고_기록하지_않는다() throws Exception {
        mvc.perform(export("2026-10-31", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        mvc.perform(export("2025-09-30", "2026-10-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/exports/routing.csv").param("from", "2026-10-01").cookie(login))
                .andExpect(status().isBadRequest());
        mvc.perform(export("어제", "2026-10-01")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log WHERE action = 'ROUTING_EXPORT'",
                Integer.class)).isZero();
    }

    @Test
    void 딱_1년은_받는다() throws Exception {
        mvc.perform(export("2025-10-01", "2026-10-01")).andExpect(status().isOk());
    }

    private String csv(String from, String to) throws Exception {
        return mvc.perform(export(from, to)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder export(String from, String to) {
        return get("/api/v1/admin/exports/routing.csv").param("from", from).param("to", to).cookie(login);
    }

    private long routing(long question, String stage, Integer rank, String state, String sentAt, String respondedAt,
            String detail) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, rank_no, state, sent_at, responded_at, score_detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, question, member.id(), stage, rank, state, sentAt, respondedAt, detail);
        return jdbc.queryForObject("SELECT MAX(id) FROM routing", Long.class);
    }

}
