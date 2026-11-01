package com.gdghost.coffee;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class CoffeeChatIntegrationTest {

    private static final String PURPOSE = "신입 백엔드 지원 전에 여쭤보고 싶습니다";
    private static final String MEMO = "카톡 senior로 연락 주세요";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakePushGateway push;

    @Autowired
    CoffeeExpireJob expireJob;

    TestMember requester;
    TestMember receiver;
    Cookie requesterLogin;
    Cookie receiverLogin;

    @BeforeEach
    void setUp() {
        push.clear();
        requester = fixtures.member();
        receiver = fixtures.member();
        requesterLogin = fixtures.loginCookie(requester);
        receiverLogin = fixtures.loginCookie(receiver);
        openCoffee(receiver);
    }

    @Test
    void 신청하면_REQUESTED로_만들고_받는_사람에게_알리고_푸시한다() throws Exception {
        String endpoint = "https://push.test/" + UUID.randomUUID();
        jdbc.update("UPDATE member SET push_on = TRUE WHERE id = ?", receiver.id());
        jdbc.update("""
                INSERT INTO push_subscription (member_id, endpoint, endpoint_hash, p256dh, auth)
                VALUES (?, ?, SHA2(endpoint, 256), 'key', 'auth')
                """, receiver.id(), endpoint);

        request(requesterLogin, receiver.id(), "  " + PURPOSE + "  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("REQUESTED"))
                .andExpect(jsonPath("$.purpose").value(PURPOSE))
                .andExpect(jsonPath("$.counterpart.id").value(receiver.id()))
                .andExpect(jsonPath("$.counterpart.nickname").value(receiver.nickname()))
                .andExpect(jsonPath("$.counterpart.status").value("ENROLLED"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").doesNotExist())
                .andExpect(jsonPath("$.coffeeMemo").doesNotExist());

        assertThat(jdbc.queryForMap("SELECT member_id, target_type FROM notification WHERE type = 'COFFEE_REQUESTED'"))
                .containsEntry("member_id", receiver.id())
                .containsEntry("target_type", "COFFEE_CHAT");
        assertThat(push.await(s -> s.payload().contains("COFFEE_REQUESTED"), 1))
                .extracting(s -> s.target().endpoint()).containsExactly(endpoint);
    }

    @Test
    void 본인에게_신청하거나_목적이_짧으면_400() throws Exception {
        request(requesterLogin, requester.id(), PURPOSE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("receiverId"));
        request(requesterLogin, receiver.id(), "          짧음     ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("purpose"));
    }

    @Test
    void 없거나_탈퇴_정지한_상대는_404이고_꺼_둔_상대는_409() throws Exception {
        request(requesterLogin, 999_999, PURPOSE).andExpect(status().isNotFound());
        request(requesterLogin, fixtures.withdrawn().id(), PURPOSE).andExpect(status().isNotFound());
        request(requesterLogin, fixtures.suspended().id(), PURPOSE).andExpect(status().isNotFound());
        request(requesterLogin, fixtures.member().id(), PURPOSE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_DISABLED"));
    }

    @Test
    void 응답_대기_중이면_409이고_반대_방향_신청은_된다() throws Exception {
        request(requesterLogin, receiver.id(), PURPOSE).andExpect(status().isCreated());

        request(requesterLogin, receiver.id(), PURPOSE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_PENDING_EXISTS"));

        openCoffee(requester);
        request(receiverLogin, requester.id(), PURPOSE).andExpect(status().isCreated());
    }

    @Test
    void 동시에_신청해도_하나만_들어간다() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tasks.add(() -> request(requesterLogin, receiver.id(), PURPOSE).andReturn().getResponse().getStatus());
        }
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        List<Integer> statuses = new ArrayList<>();
        try {
            for (Future<Integer> result : pool.invokeAll(tasks)) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409);
    }

    @Test
    void 거절_후_14일_동안은_다시_신청할_수_없고_지나면_된다() throws Exception {
        long id = requestId();
        respond(receiverLogin, id, "DECLINE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DECLINED"))
                .andExpect(jsonPath("$.counterpart.id").value(requester.id()))
                .andExpect(jsonPath("$.coffeeMemo").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT body FROM notification WHERE type = 'COFFEE_DECLINED'", String.class))
                .contains("이번엔 어렵대요");

        request(requesterLogin, receiver.id(), PURPOSE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_COOLDOWN"));
        mvc.perform(get("/api/v1/members/{id}", receiver.id()).cookie(requesterLogin))
                .andExpect(jsonPath("$.coffeeChat.myRequestState").value("DECLINED"))
                .andExpect(jsonPath("$.coffeeChat.availableAt").isNotEmpty());

        age(id, "responded_at", 14);
        request(requesterLogin, receiver.id(), PURPOSE).andExpect(status().isCreated());
    }

    @Test
    void 수락하면_그때_메모를_복사해_14일만_보여주고_되돌릴_수_없다() throws Exception {
        long id = requestId();

        respond(receiverLogin, id, "ACCEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.coffeeMemo").value(MEMO))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT member_id FROM notification WHERE type = 'COFFEE_ACCEPTED'", Long.class))
                .isEqualTo(requester.id());

        jdbc.update("UPDATE member SET coffee_memo = '바뀐 메모' WHERE id = ?", receiver.id());
        list(requesterLogin, "SENT").andExpect(jsonPath("$.items[0].coffeeMemo").value(MEMO));
        list(receiverLogin, "RECEIVED").andExpect(jsonPath("$.items[0].coffeeMemo").value(MEMO));

        respond(receiverLogin, id, "DECLINE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_ALREADY_RESPONDED"));

        age(id, "responded_at", 14);
        list(requesterLogin, "SENT").andExpect(jsonPath("$.items[0].coffeeMemo").doesNotExist());
    }

    @Test
    void 메모가_없으면_수락할_수_없고_거절은_된다() throws Exception {
        long id = requestId();
        jdbc.update("UPDATE member SET accept_coffee = FALSE, coffee_memo = NULL WHERE id = ?", receiver.id());

        respond(receiverLogin, id, "ACCEPT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("coffeeMemo"));
        respond(receiverLogin, id, "DECLINE").andExpect(status().isOk());
    }

    @Test
    void 응답은_받은_사람만_없으면_404_잘못된_action은_400() throws Exception {
        long id = requestId();

        respond(requesterLogin, id, "ACCEPT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        respond(receiverLogin, 999_999, "ACCEPT").andExpect(status().isNotFound());
        respond(receiverLogin, id, "MAYBE").andExpect(status().isBadRequest());
    }

    @Test
    void 칠일이_지나면_배치_전에도_응답할_수_없고_배치가_만료하고_신청자에게_알린다() throws Exception {
        long overdue = requestId();
        TestMember other = fixtures.member();
        openCoffee(other);
        long fresh = JsonPath.parse(request(requesterLogin, other.id(), PURPOSE).andReturn().getResponse()
                .getContentAsString()).read("$.id", Long.class);
        age(overdue, "created_at", 7);

        respond(receiverLogin, overdue, "ACCEPT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COFFEE_ALREADY_RESPONDED"));

        expireJob.run();

        assertThat(state(overdue)).isEqualTo("EXPIRED");
        assertThat(state(fresh)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForMap("""
                SELECT member_id, target_type, target_id FROM notification WHERE type = 'COFFEE_EXPIRED'
                """))
                .containsEntry("member_id", requester.id())
                .containsEntry("target_type", "COFFEE_CHAT")
                .containsEntry("target_id", overdue);
        assertThat(jdbc.queryForObject("SELECT message FROM batch_log WHERE job_name = 'coffee_expire_nightly'",
                String.class)).isEqualTo("expired=1 memoCleared=0");
        request(requesterLogin, receiver.id(), PURPOSE)
                .andExpect(jsonPath("$.code").value("COFFEE_COOLDOWN"));
    }

    @Test
    void 배치는_공개_기간이_지난_메모를_지운다() throws Exception {
        long closed = requestId();
        respond(receiverLogin, closed, "ACCEPT").andExpect(status().isOk());
        age(closed, "responded_at", 14);
        long open = requestId();
        respond(receiverLogin, open, "ACCEPT").andExpect(status().isOk());

        expireJob.run();

        assertThat(jdbc.queryForObject("SELECT accept_memo FROM coffee_chat WHERE id = ?", String.class, closed))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT accept_memo FROM coffee_chat WHERE id = ?", String.class, open))
                .isEqualTo(MEMO);
    }

    @Test
    void 정지하면_응답_대기_신청이_양쪽_모두_만료된다() throws Exception {
        long received = requestId();
        openCoffee(requester);
        TestMember third = fixtures.member();
        long sent = JsonPath.parse(request(fixtures.loginCookie(third), requester.id(), PURPOSE).andReturn()
                .getResponse().getContentAsString()).read("$.id", Long.class);
        Cookie staffLogin = fixtures.loginCookie(fixtures.staff());

        mvc.perform(post("/api/v1/ops/members/{id}/suspension", requester.id()).cookie(staffLogin).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"규칙 위반\"}"))
                .andExpect(status().isOk());

        assertThat(state(received)).isEqualTo("EXPIRED");
        assertThat(state(sent)).isEqualTo("EXPIRED");
    }

    @Test
    void 받은_사람이_탈퇴하면_메모를_지우고_탈퇴한_부원으로_보인다() throws Exception {
        long id = requestId();
        respond(receiverLogin, id, "ACCEPT").andExpect(status().isOk());

        mvc.perform(post("/api/v1/me/withdrawal").cookie(receiverLogin).with(csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT accept_memo FROM coffee_chat WHERE id = ?", String.class, id)).isNull();
        list(requesterLogin, "SENT")
                .andExpect(jsonPath("$.items[0].counterpart.id").doesNotExist())
                .andExpect(jsonPath("$.items[0].counterpart.nickname").value("탈퇴한 부원"))
                .andExpect(jsonPath("$.items[0].counterpart.withdrawn").value(true))
                .andExpect(jsonPath("$.items[0].coffeeMemo").doesNotExist());
    }

    @Test
    void 목록은_함을_나눠_최신순_커서로_준다() throws Exception {
        TestMember second = fixtures.member();
        openCoffee(second);
        long first = requestId();
        long next = JsonPath.parse(request(requesterLogin, second.id(), PURPOSE).andReturn().getResponse()
                .getContentAsString()).read("$.id", Long.class);

        String body = mvc.perform(get("/api/v1/me/coffee-chats").cookie(requesterLogin)
                        .param("box", "SENT").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) next)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/me/coffee-chats").cookie(requesterLogin).param("box", "SENT")
                        .param("cursor", JsonPath.<String>read(body, "$.nextCursor")))
                .andExpect(jsonPath("$.items[*].id", contains((int) first)))
                .andExpect(jsonPath("$.hasMore").value(false));

        list(receiverLogin, "SENT").andExpect(jsonPath("$.items").isEmpty());
        list(receiverLogin, "RECEIVED").andExpect(jsonPath("$.items[*].id", contains((int) first)));
        mvc.perform(get("/api/v1/me/coffee-chats").cookie(requesterLogin)).andExpect(status().isBadRequest());
    }

    @Test
    void 프로필은_내가_보낸_신청_상태만_보여준다() throws Exception {
        requestId();

        mvc.perform(get("/api/v1/members/{id}", receiver.id()).cookie(requesterLogin))
                .andExpect(jsonPath("$.coffeeChat.myRequestState").value("REQUESTED"))
                .andExpect(jsonPath("$.coffeeChat.availableAt").doesNotExist());
        mvc.perform(get("/api/v1/members/{id}", requester.id()).cookie(receiverLogin))
                .andExpect(jsonPath("$.coffeeChat.myRequestState").doesNotExist());
    }

    private void openCoffee(TestMember member) {
        jdbc.update("UPDATE member SET accept_coffee = TRUE, coffee_memo = ? WHERE id = ?", MEMO, member.id());
    }

    private void age(long id, String column, int days) {
        jdbc.update("UPDATE coffee_chat SET " + column + " = " + column + " - INTERVAL ? DAY WHERE id = ?", days, id);
    }

    private String state(long id) {
        return jdbc.queryForObject("SELECT state FROM coffee_chat WHERE id = ?", String.class, id);
    }

    private long requestId() throws Exception {
        String body = request(requesterLogin, receiver.id(), PURPOSE)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }

    private ResultActions request(Cookie cookie, long receiverId, String purpose) throws Exception {
        return mvc.perform(post("/api/v1/coffee-chats").cookie(cookie).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":" + receiverId + ",\"purpose\":\"" + purpose + "\"}"));
    }

    private ResultActions respond(Cookie cookie, long id, String action) throws Exception {
        return mvc.perform(patch("/api/v1/coffee-chats/{id}", id).cookie(cookie).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"));
    }

    private ResultActions list(Cookie cookie, String box) throws Exception {
        return mvc.perform(get("/api/v1/me/coffee-chats").cookie(cookie).param("box", box));
    }

}
