package com.gdghost.event;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.Role;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class OpsCertificationIntegrationTest {

    private static final String URL = "/api/v1/ops/certifications";
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
    OpsCertificationService service;

    @Autowired
    FakePushGateway push;

    @Autowired
    FakeDiscordGateway discord;

    @Autowired
    com.gdghost.routing.OpsDigestJob digest;

    private TestMember staff;
    private TestMember member;
    private long eventId;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        member = fixtures.member();
        eventId = event("챌린지");
        push.clear();
        discord.clear();
    }

    private long event(String name) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return new SimpleJdbcInsert(jdbc).withTableName("event").usingGeneratedKeyColumns("id")
                .usingColumns("created_by", "name", "start_date", "end_date")
                .executeAndReturnKey(Map.of("created_by", staff.id(), "name", name, "start_date", today.minusDays(1),
                        "end_date", today.plusDays(1)))
                .longValue();
    }

    private long cert(long event, long author) {
        return new SimpleJdbcInsert(jdbc).withTableName("certification").usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "member_id", "body")
                .executeAndReturnKey(Map.of("event_id", event, "member_id", author, "body", "한 줄")).longValue();
    }

    private ResultActions review(TestMember who, long id, String body) throws Exception {
        return mvc.perform(post(URL + "/{id}/review", id).cookie(fixtures.loginCookie(who)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(String... params) throws Exception {
        var request = get(URL).cookie(fixtures.loginCookie(staff));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    @Test
    void 대기_목록은_오래된_순이고_이벤트와_사진을_담고_이벤트로_거른다() throws Exception {
        long other = event("다른 이벤트");
        long first = cert(eventId, member.id());
        long second = cert(other, member.id());
        long approved = cert(eventId, member.id());
        jdbc.update("UPDATE certification SET state = 'APPROVED' WHERE id = ?", approved);
        jdbc.update("INSERT INTO attachment (owner_type, owner_id, object_key) VALUES ('CERT', ?, 'cert/a.webp')", first);

        list().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) first, (int) second)))
                .andExpect(jsonPath("$.items[0].event.name").value("챌린지"))
                .andExpect(jsonPath("$.items[0].author.id").value(member.id()))
                .andExpect(jsonPath("$.items[0].photos[0].url", containsString("cert/a.webp")))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0));

        list("eventId", Long.toString(other)).andExpect(jsonPath("$.items[*].id", contains((int) second)));
        list("state", "APPROVED").andExpect(jsonPath("$.items[*].id", contains((int) approved)));
        list("size", "1", "page", "1").andExpect(jsonPath("$.items[*].id", contains((int) second)))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void 다시_올린_글은_다시_올린_시각으로_줄_선다() throws Exception {
        long resubmitted = cert(eventId, member.id());
        long later = cert(eventId, member.id());
        jdbc.update("UPDATE certification SET created_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(3), resubmitted);
        jdbc.update("UPDATE certification SET created_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(2), later);
        jdbc.update("UPDATE certification SET updated_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1), resubmitted);

        list().andExpect(jsonPath("$.items[*].id", contains((int) later, (int) resubmitted)));
    }

    @Test
    void 탈퇴한_부원의_대기_인증은_목록과_대기_수에서_빠진다() throws Exception {
        cert(eventId, fixtures.withdrawn().id());
        long active = cert(eventId, member.id());

        list().andExpect(jsonPath("$.items[*].id", contains((int) active))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/events/{id}", eventId).cookie(fixtures.loginCookie(staff)))
                .andExpect(jsonPath("$.pendingCount").value(1));
    }

    @Test
    void 승인하면_도장이_찍히고_기록과_알림을_남긴다() throws Exception {
        long id = cert(eventId, member.id());
        subscribe(member);

        review(staff, id, "{\"decision\":\"APPROVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM certification WHERE id = ?", Long.class, id))
                .isEqualTo(staff.id());
        assertThat(jdbc.queryForMap("""
                SELECT type, title, body, target_type, target_id, sub_target_type, sub_target_id
                FROM notification WHERE member_id = ?
                """, member.id()))
                .containsEntry("type", "CERT_APPROVED")
                .containsEntry("body", "챌린지")
                .containsEntry("target_type", "EVENT")
                .containsEntry("target_id", eventId)
                .containsEntry("sub_target_type", "CERTIFICATION")
                .containsEntry("sub_target_id", id);
        assertThat(jdbc.queryForMap("""
                SELECT action, target_member_id, target_type, target_id FROM ops_audit_log WHERE actor_id = ?
                """, staff.id()))
                .containsEntry("action", "CERT_REVIEW")
                .containsEntry("target_member_id", member.id())
                .containsEntry("target_type", "CERTIFICATION")
                .containsEntry("target_id", id);
        assertThat(push.await(sent -> sent.payload().contains("CERT_APPROVED"), 1)).hasSize(1);
    }

    @Test
    void 반려하면_사유를_알리고_작성자가_고쳐_다시_올리면_다시_대기가_된다() throws Exception {
        long id = cert(eventId, member.id());

        review(staff, id, "{\"decision\":\"REJECT\",\"rejectReason\":\"  사진이 흐려요  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("사진이 흐려요"));
        assertThat(jdbc.queryForMap("SELECT type, body FROM notification WHERE member_id = ?", member.id()))
                .containsEntry("type", "CERT_REJECTED")
                .containsEntry("body", "사진이 흐려요");

        mvc.perform(patch("/api/v1/certifications/{id}", id).cookie(fixtures.loginCookie(member)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"다시 올려요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
        list().andExpect(jsonPath("$.items[*].id", contains((int) id)));
    }

    @Test
    void 반려_사유가_없거나_짧으면_400() throws Exception {
        long id = cert(eventId, member.id());

        review(staff, id, "{\"decision\":\"REJECT\"}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        review(staff, id, "{\"decision\":\"REJECT\",\"rejectReason\":\" \"}")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        review(staff, id, "{}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForObject("SELECT state FROM certification WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void 이미_검토했으면_409_없으면_404_부원과_개발자는_403() throws Exception {
        long id = cert(eventId, member.id());
        review(staff, id, "{\"decision\":\"APPROVE\"}").andExpect(status().isOk());

        review(staff, id, "{\"decision\":\"APPROVE\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_ALREADY_REVIEWED"));
        review(staff, 999_999, "{\"decision\":\"APPROVE\"}").andExpect(status().isNotFound());
        review(member, id, "{\"decision\":\"APPROVE\"}").andExpect(status().isForbidden());
        review(fixtures.dev(), id, "{\"decision\":\"APPROVE\"}").andExpect(status().isForbidden());
    }

    @Test
    void 두_운영진이_동시에_눌러도_한_번만_처리되고_알림도_하나다() throws Exception {
        long id = cert(eventId, member.id());
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        for (TestMember who : List.of(staff, fixtures.staff())) {
            AuthMember actor = new AuthMember(who.id(), Role.STAFF);
            tasks.add(() -> {
                try {
                    service.review(actor, id, new CertificationReviewRequest(
                            CertificationReviewRequest.Decision.APPROVE, null));
                    return null;
                } catch (BusinessException e) {
                    return e.code();
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<ErrorCode> results = new ArrayList<>();
        try {
            for (Future<ErrorCode> result : pool.invokeAll(tasks)) {
                results.add(result.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.CERT_ALREADY_REVIEWED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id = ?", Integer.class,
                member.id())).isEqualTo(1);
    }

    @Test
    void 운영진_요약에_확인_대기_수가_들어가고_없으면_보내지_않는다() {
        digest.run();
        assertThat(discord.after(java.time.Duration.ofMillis(300),
                sent -> sent.message().title().startsWith("인증 확인 대기"))).isEmpty();

        cert(eventId, member.id());
        cert(eventId, fixtures.withdrawn().id());
        digest.run();

        discord.await(sent -> sent.is(DiscordChannel.STAFF, "인증 확인 대기 1건"));
    }

    private void subscribe(TestMember who) throws Exception {
        mvc.perform(post("/api/v1/me/push-subscriptions").with(csrfToken()).cookie(fixtures.loginCookie(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID()
                        + "\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH + "\"}}"))
                .andExpect(status().isCreated());
    }

}
