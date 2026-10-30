package com.gdghost.notice;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
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

/** 공지 작성 · 운영 목록 · 수정 · 삭제 */
@IntegrationTest
class OpsNoticeIntegrationTest {

    private static final String WEB = "http://localhost:3000";

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

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        jdbc.update("UPDATE member SET staff_title = '회장' WHERE id = ?", staff.id());
        staffLogin = fixtures.loginCookie(staff);
        discord.clear();
    }

    @Test
    void 작성하면_201과_상세_모양을_주고_작성자는_확인함_부원_디스코드로_보낸다() throws Exception {
        fixtures.member();

        String body = create(UUID.randomUUID().toString(), """
                {"title":"  4기 3차 정기세션 안내  ","body":"  10/10(금) 19시  "}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("4기 3차 정기세션 안내"))
                .andExpect(jsonPath("$.body").value("10/10(금) 19시"))
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.isMustRead").value(false))
                .andExpect(jsonPath("$.dueDate").isEmpty())
                .andExpect(jsonPath("$.author.id").value(staff.id()))
                .andExpect(jsonPath("$.author.nickname").value(staff.nickname()))
                .andExpect(jsonPath("$.author.staffTitle").value("회장"))
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.comments").isEmpty())
                .andExpect(jsonPath("$.stats.memberCount").value(2))
                .andExpect(jsonPath("$.stats.readCount").value(1))
                .andExpect(jsonPath("$.stats.confirmedCount").value(1))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT author_id FROM notice WHERE id = ?", Long.class, id))
                .isEqualTo(staff.id());
        FakeDiscordGateway.Sent sent = discord.await(s -> s.is(DiscordChannel.MEMBER, "새 공지")
                && s.message().url().equals(WEB + "/notices/" + id));
        assertThat(sent.message().description()).isEqualTo("4기 3차 정기세션 안내");
    }

    @Test
    void Location은_부원용_상세_주소() throws Exception {
        String location = create(UUID.randomUUID().toString(), "{\"title\":\"공지 제목\",\"body\":\"본문\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).matches("/api/v1/notices/\\d+");
    }

    @Test
    void 필독이면_디스코드에_필독을_달고_제목의_마크다운은_글자로_보낸다() throws Exception {
        String body = create(UUID.randomUUID().toString(), """
                {"title":"**긴급** 공지","body":"본문","isPinned":false,"isMustRead":true}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPinned").value(false))
                .andExpect(jsonPath("$.isMustRead").value(true))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        FakeDiscordGateway.Sent sent = discord.await(s -> s.is(DiscordChannel.MEMBER, "[필독] 새 공지")
                && s.message().url().equals(WEB + "/notices/" + id));
        assertThat(sent.message().description()).isEqualTo("\\*\\*긴급\\*\\* 공지");
    }

    @Test
    void 마감일은_오늘부터_받는다() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        create(UUID.randomUUID().toString(), "{\"title\":\"공지 제목\",\"body\":\"본문\",\"dueDate\":\"" + today + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value(today.toString()));
        create(UUID.randomUUID().toString(),
                "{\"title\":\"공지 제목\",\"body\":\"본문\",\"dueDate\":\"" + today.minusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notice", Integer.class)).isOne();
    }

    @Test
    void 제목이_짧거나_본문이_비면_400_저장도_전송도_없다() throws Exception {
        create(UUID.randomUUID().toString(), "{\"title\":\" 가 \",\"body\":\"본문\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        create(UUID.randomUUID().toString(), "{\"title\":\"" + "가".repeat(81) + "\",\"body\":\"본문\"}")
                .andExpect(status().isBadRequest());
        create(UUID.randomUUID().toString(), "{\"title\":\"공지 제목\",\"body\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        create(UUID.randomUUID().toString(), "{\"title\":\"공지 제목\",\"body\":\"" + "가".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notice", Integer.class)).isZero();
        assertThat(discord.after(Duration.ofMillis(300), s -> s.message().title().endsWith("새 공지"))).isEmpty();
    }

    @Test
    void 같은_키로_다시_보내면_하나만_만들고_한_번만_보낸다() throws Exception {
        String key = UUID.randomUUID().toString();
        String content = "{\"title\":\"한 번만\",\"body\":\"본문\"}";
        String first = create(key, content).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        create(key, content)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", first));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notice", Integer.class)).isOne();
        String url = WEB + first.replace("/api/v1", "");
        discord.await(s -> s.message().url().equals(url));
        assertThat(discord.after(Duration.ofMillis(300), s -> s.message().url().equals(url))).hasSize(1);
    }

    @Test
    void 목록은_최신순_지운_공지_제외_탈퇴_정지는_집계에서_뺀다() throws Exception {
        TestMember reader = fixtures.member();
        TestMember suspended = fixtures.suspended();
        TestMember withdrawn = fixtures.withdrawn();
        long older = fixtures.notice(staff.id());
        long newer = fixtures.notice(staff.id());
        long deleted = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET created_at = created_at - INTERVAL 1 HOUR WHERE id = ?", older);
        jdbc.update("UPDATE notice SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);
        jdbc.update("UPDATE notice SET is_must_read = TRUE, due_date = '2099-01-31' WHERE id = ?", newer);
        read(newer, reader.id(), true);
        read(newer, staff.id(), false);
        read(newer, suspended.id(), true);
        read(newer, withdrawn.id(), true);
        comment(newer, reader.id(), false);
        comment(newer, reader.id(), false);
        comment(newer, reader.id(), true);

        mvc.perform(get("/api/v1/ops/notices").cookie(staffLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(newer))
                .andExpect(jsonPath("$.items[1].id").value(older))
                .andExpect(jsonPath("$.items[0].isPinned").value(false))
                .andExpect(jsonPath("$.items[0].isMustRead").value(true))
                .andExpect(jsonPath("$.items[0].dueDate").value("2099-01-31"))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].author.id").value(staff.id()))
                .andExpect(jsonPath("$.items[0].author.nickname").value(staff.nickname()))
                .andExpect(jsonPath("$.items[0].stats.memberCount").value(2))
                .andExpect(jsonPath("$.items[0].stats.readCount").value(2))
                .andExpect(jsonPath("$.items[0].stats.confirmedCount").value(1))
                .andExpect(jsonPath("$.items[0].commentCount").value(2))
                .andExpect(jsonPath("$.items[0].remindedAt").isEmpty())
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[1].stats.readCount").value(0))
                .andExpect(jsonPath("$.items[1].commentCount").value(0));
    }

    @Test
    void 목록의_탈퇴한_작성자는_id만_남긴다() throws Exception {
        TestMember withdrawn = fixtures.withdrawn();
        fixtures.notice(withdrawn.id());

        mvc.perform(get("/api/v1/ops/notices").cookie(staffLogin).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].author.id").value(withdrawn.id()))
                .andExpect(jsonPath("$.items[0].author.nickname").isEmpty());
    }

    @Test
    void 수정은_보낸_필드만_바꾸고_다른_운영진의_공지도_고칠_수_있다() throws Exception {
        long notice = fixtures.notice(fixtures.staff().id());
        jdbc.update("UPDATE notice SET is_pinned = TRUE, due_date = '2099-01-31' WHERE id = ?", notice);
        String title = jdbc.queryForObject("SELECT title FROM notice WHERE id = ?", String.class, notice);

        update(notice, "{\"isPinned\":false,\"body\":\"  고친 본문  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notice))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.body").value("고친 본문"))
                .andExpect(jsonPath("$.isPinned").value(false))
                .andExpect(jsonPath("$.dueDate").value("2099-01-31"))
                .andExpect(jsonPath("$.confirmed").value(false));

        assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM notice WHERE id = ?", Boolean.class,
                notice)).isTrue();
        assertThat(discord.after(Duration.ofMillis(300),
                s -> s.message().url().equals(WEB + "/notices/" + notice))).isEmpty();
    }

    @Test
    void clearDueDate면_마감일을_지워_상시로() throws Exception {
        long notice = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET due_date = '2099-01-31' WHERE id = ?", notice);

        update(notice, "{\"clearDueDate\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").isEmpty());
    }

    @Test
    void 마감일과_지우기를_함께_보내거나_아무것도_안_보내면_400() throws Exception {
        long notice = fixtures.notice(staff.id());

        update(notice, "{\"dueDate\":\"2099-01-31\",\"clearDueDate\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        update(notice, "{}").andExpect(status().isBadRequest());
        update(notice, "{\"clearDueDate\":false}").andExpect(status().isBadRequest());
    }

    @Test
    void 지난_마감일로는_못_바꾸지만_마감이_지난_공지의_다른_필드는_고칠_수_있다() throws Exception {
        long notice = fixtures.notice(staff.id());
        jdbc.update("UPDATE notice SET due_date = '2020-01-01' WHERE id = ?", notice);

        update(notice, "{\"dueDate\":\"2020-01-02\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
        update(notice, "{\"title\":\"새 제목입니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2020-01-01"));
    }

    @Test
    void 바뀐_게_없으면_수정_시각을_찍지_않는다() throws Exception {
        long notice = fixtures.notice(staff.id());
        String title = jdbc.queryForObject("SELECT title FROM notice WHERE id = ?", String.class, notice);

        update(notice, "{\"title\":\"" + title + "\",\"isPinned\":false}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT updated_at IS NULL FROM notice WHERE id = ?", Boolean.class,
                notice)).isTrue();
    }

    @Test
    void 삭제하면_204_목록에서_빠지고_다시_고치거나_지우면_404() throws Exception {
        long notice = fixtures.notice(staff.id());

        mvc.perform(delete("/api/v1/ops/notices/{id}", notice).cookie(staffLogin).with(csrfToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM notice WHERE id = ?", Boolean.class,
                notice)).isTrue();
        mvc.perform(get("/api/v1/ops/notices").cookie(staffLogin))
                .andExpect(jsonPath("$.total").value(0));
        update(notice, "{\"isPinned\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(delete("/api/v1/ops/notices/{id}", notice).cookie(staffLogin).with(csrfToken()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/ops/notices/{id}", Long.MAX_VALUE).cookie(staffLogin).with(csrfToken()))
                .andExpect(status().isNotFound());
    }

    private ResultActions create(String key, String content) throws Exception {
        return mvc.perform(post("/api/v1/ops/notices").cookie(staffLogin).with(csrfToken())
                .header(IdempotencyAspect.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private ResultActions update(long noticeId, String content) throws Exception {
        return mvc.perform(patch("/api/v1/ops/notices/{id}", noticeId).cookie(staffLogin).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private void read(long noticeId, long memberId, boolean confirmed) {
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, confirmed_at) VALUES (?, ?, "
                + (confirmed ? "UTC_TIMESTAMP(6)" : "NULL") + ")", noticeId, memberId);
    }

    private void comment(long noticeId, long authorId, boolean deleted) {
        jdbc.update("INSERT INTO notice_comment (notice_id, author_id, body, deleted_at) VALUES (?, ?, '댓글', "
                + (deleted ? "UTC_TIMESTAMP(6)" : "NULL") + ")", noticeId, authorId);
    }

}
