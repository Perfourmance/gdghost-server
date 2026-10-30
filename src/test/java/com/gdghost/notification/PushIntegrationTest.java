package com.gdghost.notification;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

/** 공개키 · 구독 등록 · 해제 · 어떤 알림이 푸시로 가는지 */
@IntegrationTest
class PushIntegrationTest {

    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakePushGateway push;

    @BeforeEach
    void clear() {
        push.clear();
    }

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    @Test
    void 공개키는_하루_캐시로_준다() throws Exception {
        mvc.perform(get("/api/v1/push/vapid-public-key").cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400"))
                .andExpect(jsonPath("$.publicKey").isNotEmpty());
    }

    @Test
    void 등록하면_201이고_push_on이_켜진다() throws Exception {
        TestMember me = fixtures.member();

        subscribe(me, endpoint()).andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber());

        assertThat(jdbc.queryForObject("SELECT push_on FROM member WHERE id = ?", Boolean.class, me.id())).isTrue();
    }

    @Test
    void 이미_있는_주소면_200이고_주인이_바뀐다() throws Exception {
        TestMember first = fixtures.member();
        TestMember second = fixtures.member();
        String endpoint = endpoint();
        subscribe(first, endpoint).andExpect(status().isCreated());

        subscribe(second, endpoint).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT member_id FROM push_subscription", Long.class))
                .containsExactly(second.id());
    }

    @Test
    void 푸시_서비스가_아닌_주소와_잘못된_키는_400() throws Exception {
        TestMember me = fixtures.member();

        subscribe(me, "https://169.254.169.254/latest/meta-data").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endpoint"));
        subscribe(me, endpoint(), "short", AUTH).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("keys.p256dh"));
        subscribe(me, endpoint(), P256DH, "!!!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("keys.auth"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class)).isZero();
    }

    @Test
    void 해제는_내_것만_지우고_없는_주소도_204() throws Exception {
        TestMember me = fixtures.member();
        TestMember other = fixtures.member();
        String mine = endpoint();
        String others = endpoint();
        subscribe(me, mine);
        subscribe(other, others);

        unsubscribe(me, mine).andExpect(status().isNoContent());
        unsubscribe(me, others).andExpect(status().isNoContent());
        unsubscribe(me, endpoint()).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT member_id FROM push_subscription", Long.class))
                .containsExactly(other.id());
    }

    @Test
    void 답변_알림은_질문자의_모든_기기로_간다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        String phone = endpoint();
        String laptop = endpoint();
        subscribe(author, phone);
        subscribe(author, laptop);
        subscribe(answerer, endpoint());
        long question = fixtures.question(author.id(), false);

        answer(answerer, question);

        assertThat(push.await(s -> s.payload().contains("NEW_ANSWER"), 2))
                .extracting(s -> s.target().endpoint()).containsExactlyInAnyOrder(phone, laptop);
        FakePushGateway.Sent sent = push.await(s -> s.payload().contains("NEW_ANSWER"), 1).getFirst();
        assertThat(sent.payload()).contains("\"target\":{\"type\":\"QUESTION\"").contains("\"subType\":\"ANSWER\"");
    }

    @Test
    void 새_질문은_앱_내_알림만이고_푸시는_없다() throws Exception {
        TestMember author = fixtures.member();
        TestMember other = fixtures.member();
        subscribe(other, endpoint());

        mvc.perform(post("/api/v1/questions").with(csrfToken()).cookie(fixtures.loginCookie(author))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"JOB\",\"title\":\"질문 제목\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'NEW_QUESTION'", Integer.class))
                .isEqualTo(1);
        assertThat(push.after(Duration.ofMillis(300), s -> true)).isEmpty();
    }

    @Test
    void 필독_공지만_푸시로_가고_작성자는_빠진다() throws Exception {
        TestMember staff = fixtures.staff();
        TestMember member = fixtures.member();
        String staffPhone = endpoint();
        String memberPhone = endpoint();
        subscribe(staff, staffPhone);
        subscribe(member, memberPhone);

        notice(staff, "일반 공지", false);
        assertThat(push.after(Duration.ofMillis(300), s -> true)).isEmpty();

        notice(staff, "필독 공지", true);
        assertThat(push.await(s -> s.payload().contains("필독 공지"), 1))
                .extracting(s -> s.target().endpoint()).containsExactly(memberPhone);
        assertThat(push.after(Duration.ofMillis(200), s -> s.target().endpoint().equals(staffPhone))).isEmpty();
    }

    @Test
    void 푸시를_끈_사람에게는_보내지_않아도_앱_내_알림은_남는다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        subscribe(author, endpoint());
        jdbc.update("UPDATE member SET push_on = FALSE WHERE id = ?", author.id());
        long question = fixtures.question(author.id(), false);

        answer(answerer, question);

        assertThat(push.after(Duration.ofMillis(300), s -> true)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'NEW_ANSWER'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 사라진_구독은_지우고_한_기기가_실패해도_나머지는_보낸다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        String gone = endpoint();
        String failing = endpoint();
        String healthy = endpoint();
        subscribe(author, gone);
        subscribe(author, failing);
        subscribe(author, healthy);
        push.gone(gone);
        push.fail(failing);
        long question = fixtures.question(author.id(), false);

        answer(answerer, question);

        push.await(s -> s.target().endpoint().equals(healthy), 1);
        waitUntilSubscriptionCount(2);
        assertThat(jdbc.queryForList("SELECT endpoint FROM push_subscription", String.class))
                .containsExactlyInAnyOrder(failing, healthy);
        waitUntilErrorLogged();
    }

    @Test
    void 요청이_실패해_롤백되면_푸시도_없다() throws Exception {
        TestMember author = fixtures.member();
        TestMember answerer = fixtures.member();
        subscribe(answerer, endpoint());
        long question = fixtures.question(author.id(), false);

        mvc.perform(post("/api/v1/questions/{q}/answers", question).with(csrfToken())
                .cookie(fixtures.loginCookie(author))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"내 질문에 답변\"}"))
                .andExpect(status().isConflict());

        assertThat(push.after(Duration.ofMillis(200), s -> true)).isEmpty();
    }

    @Test
    void 운영진_지정은_대상자에게_푸시로_간다() throws Exception {
        TestMember dev = fixtures.dev();
        TestMember member = fixtures.member();
        String phone = endpoint();
        subscribe(member, phone);

        mvc.perform(put("/api/v1/admin/members/{id}/staff", member.id()).with(csrfToken())
                .cookie(fixtures.loginCookie(dev)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffTitle\":\"회장\"}"))
                .andExpect(status().isOk());

        assertThat(push.await(s -> s.payload().contains("STAFF_ROLE_CHANGED"), 1))
                .extracting(s -> s.target().endpoint()).containsExactly(phone);
    }

    private void waitUntilSubscriptionCount(int count) throws InterruptedException {
        for (int i = 0; i < 250; i++) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class) == count) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("구독 수가 " + count + "가 되지 않음");
    }

    private void waitUntilErrorLogged() throws InterruptedException {
        for (int i = 0; i < 250; i++) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM error_log WHERE path = 'push NEW_ANSWER'",
                    Integer.class) > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("error_log에 기록되지 않음");
    }

    private ResultActions subscribe(TestMember member, String endpoint) throws Exception {
        return subscribe(member, endpoint, P256DH, AUTH);
    }

    private ResultActions subscribe(TestMember member, String endpoint, String p256dh, String auth) throws Exception {
        return mvc.perform(post("/api/v1/me/push-subscriptions").with(csrfToken())
                .cookie(fixtures.loginCookie(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\""
                        + auth + "\"}}"));
    }

    private ResultActions unsubscribe(TestMember member, String endpoint) throws Exception {
        return mvc.perform(delete("/api/v1/me/push-subscriptions").with(csrfToken())
                .cookie(fixtures.loginCookie(member)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + endpoint + "\"}"));
    }

    private void answer(TestMember author, long question) throws Exception {
        mvc.perform(post("/api/v1/questions/{q}/answers", question).with(csrfToken())
                .cookie(fixtures.loginCookie(author))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"답변 본문입니다\"}"))
                .andExpect(status().isCreated());
    }

    private void notice(TestMember staff, String title, boolean mustRead) throws Exception {
        mvc.perform(post("/api/v1/ops/notices").with(csrfToken()).cookie(fixtures.loginCookie(staff))
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"body\":\"본문\",\"isMustRead\":" + mustRead + "}"))
                .andExpect(status().isCreated());
    }

}
