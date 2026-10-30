package com.gdghost.notification;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** 알림 API와 기능별 알림 생성 */
@IntegrationTest
class NotificationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 질문을_쓰면_쓴_사람과_탈퇴_정지를_뺀_전원에게_알린다() throws Exception {
        TestMember author = fixtures.member();
        TestMember other = fixtures.member();
        TestMember staff = fixtures.staff();
        TestMember suspended = fixtures.suspended();
        TestMember withdrawn = fixtures.withdrawn();

        long question = writeQuestion(author, "이력서 질문입니다", false);

        assertThat(receivers("NEW_QUESTION")).containsExactlyInAnyOrder(other.id(), staff.id())
                .doesNotContain(author.id(), suspended.id(), withdrawn.id());
        mvc.perform(get("/api/v1/notifications").cookie(fixtures.loginCookie(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("NEW_QUESTION"))
                .andExpect(jsonPath("$.items[0].title").value("새 질문이 올라왔어요"))
                .andExpect(jsonPath("$.items[0].body").value("이력서 질문입니다"))
                .andExpect(jsonPath("$.items[0].target.type").value("QUESTION"))
                .andExpect(jsonPath("$.items[0].target.id").value(question))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void 답변은_질문자에게_닉네임과_답변_위치로_알린다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        long question = fixtures.question(author.id(), false);

        long answer = writeAnswer(answerer, question, "답변 본문입니다", null, false);

        assertThat(receivers("NEW_ANSWER")).containsExactly(author.id());
        mvc.perform(get("/api/v1/notifications").cookie(fixtures.loginCookie(author)))
                .andExpect(jsonPath("$.items[0].title").value(answerer.nickname() + "님이 내 질문에 답했어요"))
                .andExpect(jsonPath("$.items[0].target.subType").value("ANSWER"))
                .andExpect(jsonPath("$.items[0].target.subId").value(answer));
    }

    @Test
    void 익명_답변_알림에는_닉네임이_없다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        long question = fixtures.question(author.id(), true);

        writeAnswer(answerer, question, "익명 답변 본문", null, true);

        String title = jdbc.queryForObject("SELECT title FROM notification WHERE type = 'NEW_ANSWER'", String.class);
        assertThat(title).isEqualTo("익명1님이 내 질문에 답했어요").doesNotContain(answerer.nickname());
    }

    @Test
    void 답글은_부모_작성자와_질문자_모두에게_알린다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        TestMember replier = fixtures.member();
        long question = fixtures.question(author.id(), false);
        long answer = fixtures.answer(question, answerer.id(), false, null);

        writeAnswer(replier, question, "답글", answer, false);

        assertThat(receivers("NEW_REPLY")).containsExactly(answerer.id());
        assertThat(receivers("NEW_ANSWER")).containsExactly(author.id());
    }

    @Test
    void 자기_글에_단_답글은_알리지_않고_질문자가_부모_작성자면_하나만_간다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        long question = fixtures.question(author.id(), false);
        long answer = fixtures.answer(question, answerer.id(), false, null);

        writeAnswer(answerer, question, "내 답변에 덧붙임", answer, false);
        assertThat(receivers("NEW_REPLY")).isEmpty();
        assertThat(receivers("NEW_ANSWER")).containsExactly(author.id());

        long own = fixtures.answer(question, author.id(), false, answer);
        jdbc.update("DELETE FROM notification");
        writeAnswer(answerer, question, "질문자 답글에 답글", own, false);
        assertThat(receivers("NEW_REPLY")).containsExactly(author.id());
        assertThat(receivers("NEW_ANSWER")).isEmpty();
    }

    @Test
    void 채택은_답변자에게_한_번만_알린다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        long question = fixtures.question(author.id(), false);
        long answer = fixtures.answer(question, answerer.id(), false, null);

        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/questions/{q}/adoption", question).with(csrfToken())
                    .cookie(fixtures.loginCookie(author)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"answerId\":" + answer + "}"))
                    .andExpect(status().isOk());
        }

        assertThat(receivers("ANSWER_ADOPTED")).containsExactly(answerer.id());
    }

    @Test
    void 공지는_작성자를_뺀_전원에게_필독_여부를_담아_알린다() throws Exception {
        TestMember staff = fixtures.staff();
        TestMember member = fixtures.member();

        mvc.perform(post("/api/v1/ops/notices").with(csrfToken()).cookie(fixtures.loginCookie(staff))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"정기 모임\",\"body\":\"본문\",\"isMustRead\":true}"))
                .andExpect(status().isCreated());

        assertThat(receivers("NOTICE")).containsExactly(member.id());
        assertThat(jdbc.queryForObject("SELECT title FROM notification", String.class)).isEqualTo("필독 공지가 올라왔어요");
    }

    @Test
    void 운영진_지정은_대상자에게_설정_화면으로_알린다() throws Exception {
        TestMember dev = fixtures.dev();
        TestMember member = fixtures.member();

        mvc.perform(put("/api/v1/admin/members/{id}/staff", member.id()).with(csrfToken())
                .cookie(fixtures.loginCookie(dev)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffTitle\":\"회장\"}"))
                .andExpect(status().isOk());

        assertThat(receivers("STAFF_ROLE_CHANGED")).containsExactly(member.id());
        assertThat(jdbc.queryForObject("SELECT target_type FROM notification", String.class)).isEqualTo("SETTINGS");
    }

    @Test
    void 목록은_최신순_커서로_내_것만_준다() throws Exception {
        TestMember me = fixtures.member();
        TestMember other = fixtures.member();
        long first = notification(me);
        long second = notification(me);
        long third = notification(me);
        notification(other);

        String body = mvc.perform(get("/api/v1/notifications").param("size", "2").cookie(fixtures.loginCookie(me)))
                .andExpect(jsonPath("$.items[*].id", contains((int) third, (int) second)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        mvc.perform(get("/api/v1/notifications").param("size", "2")
                .param("cursor", JsonPath.<String>read(body, "$.nextCursor")).cookie(fixtures.loginCookie(me)))
                .andExpect(jsonPath("$.items[*].id", contains((int) first)))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void 목록을_열어도_읽음이_되지_않고_잘못된_커서는_400() throws Exception {
        TestMember me = fixtures.member();
        notification(me);

        mvc.perform(get("/api/v1/notifications").cookie(fixtures.loginCookie(me))).andExpect(status().isOk());
        unreadCountIs(me, 1);

        mvc.perform(get("/api/v1/notifications").param("cursor", "bm90LWEtbnVtYmVy").cookie(fixtures.loginCookie(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    @Test
    void 고른_것만_읽음이_되고_남의_알림은_조용히_무시한다() throws Exception {
        TestMember me = fixtures.member();
        TestMember other = fixtures.member();
        long mine = notification(me);
        notification(me);
        long others = notification(other);

        read(me, "{\"ids\":[" + mine + "," + others + "]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        unreadCountIs(other, 1);
        mvc.perform(get("/api/v1/notifications").cookie(fixtures.loginCookie(me)))
                .andExpect(jsonPath("$.items[1].read").value(true))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void 모두_읽음은_내_것_전부만() throws Exception {
        TestMember me = fixtures.member();
        TestMember other = fixtures.member();
        notification(me);
        notification(me);
        notification(other);

        read(me, "{\"all\":true}").andExpect(jsonPath("$.unreadCount").value(0));

        unreadCountIs(me, 0);
        unreadCountIs(other, 1);
    }

    @Test
    void 읽음_요청은_ids와_all_중_하나이고_100개까지() throws Exception {
        TestMember me = fixtures.member();
        String tooMany = LongStream.rangeClosed(1, 101).boxed().map(String::valueOf)
                .collect(Collectors.joining(","));

        for (String body : List.of("{}", "{\"ids\":[]}", "{\"all\":false}", "{\"ids\":[1],\"all\":true}",
                "{\"ids\":[null]}", "{\"ids\":[" + tooMany + "]}")) {
            read(me, body).andExpect(status().isBadRequest());
        }
    }

    @Test
    void 내_세션_확인에_안_읽은_수가_담긴다() throws Exception {
        TestMember me = fixtures.member();
        notification(me);
        notification(me);

        mvc.perform(get("/api/v1/auth/me").cookie(fixtures.loginCookie(me)))
                .andExpect(jsonPath("$.unreadNotificationCount").value(2));
    }

    @Test
    void 탈퇴하면_받은_알림을_지운다() throws Exception {
        TestMember me = fixtures.member();
        notification(me);

        mvc.perform(post("/api/v1/me/withdrawal").with(csrfToken()).cookie(fixtures.loginCookie(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class)).isZero();
    }

    private ResultActions read(TestMember member, String body) throws Exception {
        return mvc.perform(post("/api/v1/notifications/read").with(csrfToken()).cookie(fixtures.loginCookie(member))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void unreadCountIs(TestMember member, int count) throws Exception {
        mvc.perform(get("/api/v1/notifications/unread-count").cookie(fixtures.loginCookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(count));
    }

    private List<Long> receivers(String type) {
        return jdbc.queryForList("SELECT member_id FROM notification WHERE type = ?", Long.class, type);
    }

    private long notification(TestMember member) {
        return new SimpleJdbcInsert(jdbc).withTableName("notification")
                .usingGeneratedKeyColumns("id")
                .usingColumns("member_id", "type", "title", "target_type", "target_id")
                .executeAndReturnKey(Map.of("member_id", member.id(), "type", "NOTICE", "title", "제목",
                        "target_type", "NOTICE", "target_id", 1L))
                .longValue();
    }

    private long writeQuestion(TestMember author, String title, boolean anonymous) throws Exception {
        String body = mvc.perform(post("/api/v1/questions").with(csrfToken()).cookie(fixtures.loginCookie(author))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"JOB\",\"title\":\"" + title + "\",\"anonymous\":" + anonymous + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long writeAnswer(TestMember author, long question, String text, Long parentId, boolean anonymous)
            throws Exception {
        // 5초 규칙 — 앞선 답변을 뒤로 민다
        jdbc.update("UPDATE answer SET created_at = created_at - INTERVAL 1 MINUTE WHERE author_id = ?", author.id());
        String body = mvc.perform(post("/api/v1/questions/{q}/answers", question).with(csrfToken())
                .cookie(fixtures.loginCookie(author))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + text + "\",\"parentId\":" + parentId + ",\"anonymous\":" + anonymous + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

}
