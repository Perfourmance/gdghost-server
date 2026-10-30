package com.gdghost.common.discord;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.ops.MemberUnsuspendedEvent;
import com.gdghost.ops.OpsDiscordListener;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakeDiscordGateway.Sent;
import com.gdghost.support.FakeMailGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** 도메인 이벤트 → 디스코드 채널 (커밋 후 비동기) */
@IntegrationTest
class DiscordAlertIntegrationTest {

    private static final String WEB = "http://localhost:3000";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeDiscordGateway discord;

    @Autowired
    FakeMailGateway mail;

    @Autowired
    OpsDiscordListener opsListener;

    TestMember member;
    Cookie memberLogin;

    @BeforeEach
    void setUp() {
        discord.clear();
        mail.clear();
        member = fixtures.member();
        memberLogin = fixtures.loginCookie(member);
    }

    @Test
    void 새_질문은_부원_채널에_분류_제목_링크만() throws Exception {
        String body = mvc.perform(post("/api/v1/questions").cookie(memberLogin).with(csrfToken())
                        .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"JOB\",\"title\":\"[링크](x) 이력서 질문\",\"body\":\"본문은 안 감\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        Sent sent = discord.await(s -> s.is(DiscordChannel.MEMBER, "새 질문 · 취업·채용")
                && s.message().url().equals(WEB + "/questions/" + id));
        assertThat(sent.message().description()).isEqualTo("\\[링크\\]\\(x\\) 이력서 질문")
                .doesNotContain(member.nickname());
    }

    @Test
    void 실패한_요청은_보내지_않는다() throws Exception {
        long question = fixtures.question(member.id(), false);
        report("QUESTION", question, "처음 신고").andExpect(status().isCreated());
        report("QUESTION", question, "두 번째 신고").andExpect(status().isConflict());

        discord.await(s -> s.is(DiscordChannel.STAFF, "신고 접수 · 질문")
                && s.message().description().equals("사유 처음 신고"));
        assertThat(discord.after(Duration.ofMillis(300), s -> s.message().description() != null
                && s.message().description().contains("두 번째 신고"))).isEmpty();
    }

    @Test
    void 신고는_운영진_서버에_종류_사유_링크_신고자는_없이() throws Exception {
        TestMember author = fixtures.member();
        long question = fixtures.question(author.id(), false);
        long answer = fixtures.answer(question, author.id(), false, null);

        report("ANSWER", answer, "광고 글입니다").andExpect(status().isCreated());

        Sent sent = discord.await(s -> s.is(DiscordChannel.STAFF, "신고 접수 · 답변")
                && s.message().url().equals(WEB + "/questions/" + question + "#answer-" + answer));
        assertThat(sent.message().description()).isEqualTo("사유 광고 글입니다")
                .doesNotContain(member.nickname());
    }

    @Test
    void 정지와_해제는_운영진_서버에_누가_누구를() throws Exception {
        TestMember staff = fixtures.staff();
        Cookie staffLogin = fixtures.loginCookie(staff);

        mvc.perform(post("/api/v1/ops/members/{id}/suspension", member.id()).cookie(staffLogin).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"규칙 반복 위반\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/ops/members/{id}/suspension", member.id()).cookie(staffLogin)
                .with(csrfToken())).andExpect(status().isNoContent());

        assertThat(discord.await(s -> s.is(DiscordChannel.STAFF, "계정 정지")
                && s.message().description().startsWith(staff.nickname() + " → " + member.nickname())).message()
                .description()).endsWith("\n사유 규칙 반복 위반");
        discord.await(s -> s.is(DiscordChannel.STAFF, "계정 정지 해제")
                && s.message().description().equals(staff.nickname() + " → " + member.nickname()));
    }

    @Test
    void 이미_정지된_부원을_다시_정지하면_보내지_않는다() throws Exception {
        TestMember staff = fixtures.staff();
        Cookie staffLogin = fixtures.loginCookie(staff);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ops/members/{id}/suspension", member.id()).cookie(staffLogin)
                    .with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"규칙 위반\"}"))
                    .andExpect(status().isOk());
        }

        discord.await(s -> s.is(DiscordChannel.STAFF, "계정 정지")
                && s.message().description().contains(member.nickname()));
        assertThat(discord.after(Duration.ofMillis(300), s -> s.is(DiscordChannel.STAFF, "계정 정지")
                && s.message().description().contains(member.nickname()))).hasSize(1);
    }

    @Test
    void 개발자가_첫_운영진을_지정하면_운영진_개발자_서버_모두() throws Exception {
        TestMember dev = fixtures.dev();

        mvc.perform(put("/api/v1/admin/members/{memberId}/staff", member.id()).cookie(fixtures.loginCookie(dev))
                        .with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("{\"staffTitle\":\"회장\"}"))
                .andExpect(status().isOk());

        String expected = dev.nickname() + " → " + member.nickname() + " (회장)";
        discord.await(s -> s.is(DiscordChannel.STAFF, "운영진 지정") && s.message().description().equals(expected));
        discord.await(s -> s.is(DiscordChannel.DEV, "운영진 지정") && s.message().description().equals(expected));
    }

    @Test
    void 어드민_이메일_변경은_요청과_완료_모두_기록하고_주소는_보내지_않는다() throws Exception {
        TestMember dev = fixtures.dev();
        mvc.perform(post("/api/v1/admin/members/{memberId}/email-change", member.id())
                        .cookie(fixtures.loginCookie(dev)).with(csrfToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + Fixtures.PASSWORD
                                + "\",\"newEmail\":\"discord-new@gmail.com\",\"identityNote\":\"얼굴 아는 사이\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/email-change/confirm").with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + tokenFromMail() + "\"}"))
                .andExpect(status().isNoContent());

        for (DiscordChannel channel : new DiscordChannel[] {DiscordChannel.STAFF, DiscordChannel.DEV}) {
            Sent requested = discord.await(s -> s.is(channel, "이메일 변경 요청 (어드민)")
                    && s.message().description().startsWith(dev.nickname() + " → " + member.nickname()));
            Sent changed = discord.await(s -> s.is(channel, "이메일 변경 완료 (어드민 요청)")
                    && s.message().description().startsWith(member.nickname() + " · 요청 " + dev.nickname()));
            assertThat(requested.message().description() + changed.message().description())
                    .doesNotContain("@").doesNotContain("얼굴 아는 사이");
        }
    }

    @Test
    void 탈퇴한_부원은_탈퇴한_부원으로() throws Exception {
        TestMember staff = fixtures.staff();
        TestMember withdrawn = fixtures.withdrawn();
        // 정지 API는 탈퇴자를 404로 막으므로 리스너를 바로 부른다

        opsListener.on(new MemberUnsuspendedEvent(staff.id(), withdrawn.id()));

        discord.await(s -> s.is(DiscordChannel.STAFF, "계정 정지 해제")
                && s.message().description().equals(staff.nickname() + " → 탈퇴한 부원"));
    }

    @Test
    void 전송이_실패해도_요청은_성공하고_error_log에만_남는다() throws Exception {
        discord.failWith(new DiscordSendException("STAFF 전송 실패: HTTP 500"));
        long question = fixtures.question(member.id(), false);

        report("QUESTION", question, "실패해도 접수").andExpect(status().isCreated());

        assertThat(awaitErrorPath("discord STAFF")).isTrue();
        assertThat(jdbc.queryForObject("SELECT alerted_at FROM error_log WHERE path = 'discord STAFF'",
                Object.class)).isNull();
    }

    private boolean awaitErrorPath(String path) throws InterruptedException {
        for (int i = 0; i < 250; i++) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM error_log WHERE path = ?", Integer.class, path) > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private ResultActions report(String type, long targetId, String reason)
            throws Exception {
        return mvc.perform(post("/api/v1/reports").cookie(memberLogin).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"" + type + "\",\"targetId\":" + targetId + ",\"reason\":\"" + reason
                        + "\"}"));
    }

    private String tokenFromMail() {
        mail.awaitSent(2);
        Matcher token = Pattern.compile("token=([\\w-]+)").matcher(mail.sent().stream()
                .filter(sent -> sent.text().contains("token="))
                .findFirst().orElseThrow().text());
        assertThat(token.find()).isTrue();
        return token.group(1);
    }

}
