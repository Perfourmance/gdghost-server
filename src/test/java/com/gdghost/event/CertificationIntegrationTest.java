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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.member.MemberWithdrawalWriter;
import com.gdghost.support.FakeObjectStorage;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.gdghost.upload.UploadCleanupJob;
import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class CertificationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeObjectStorage storage;

    @Autowired
    MemberWithdrawalWriter withdrawal;

    @Autowired
    UploadCleanupJob cleanupJob;

    private TestMember member;
    private LocalDate today;
    private long eventId;

    @BeforeEach
    void setUp() {
        storage.clear();
        member = fixtures.member();
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        eventId = event(today.minusDays(1), today.plusDays(1));
    }

    private long event(LocalDate start, LocalDate end) {
        return new SimpleJdbcInsert(jdbc).withTableName("event").usingGeneratedKeyColumns("id")
                .usingColumns("created_by", "name", "start_date", "end_date")
                .executeAndReturnKey(Map.of("created_by", fixtures.staff().id(), "name", "챌린지", "start_date", start,
                        "end_date", end))
                .longValue();
    }

    private String photo(TestMember owner) {
        String key = "cert/2026/10/%d/%s.webp".formatted(owner.id(), UUID.randomUUID().toString().replace("-", ""));
        storage.put(key, 1000, Instant.now());
        return key;
    }

    private ResultActions submit(TestMember who, long event, String body) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/certifications", event).cookie(fixtures.loginCookie(who))
                .with(csrfToken()).header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions resubmit(TestMember who, long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/certifications/{id}", id).cookie(fixtures.loginCookie(who)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String text, String... keys) {
        return """
                {"body":"%s","photoKeys":["%s"]}""".formatted(text, String.join("\",\"", keys));
    }

    private long submitted(TestMember who) throws Exception {
        String response = submit(who, eventId, body("한 줄", photo(who))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long rejected(TestMember who) throws Exception {
        long id = submitted(who);
        jdbc.update("""
                UPDATE certification SET state = 'REJECTED', reject_reason = '흐려요', reviewed_by = ?,
                       reviewed_at = UTC_TIMESTAMP(6) WHERE id = ?
                """, fixtures.staff().id(), id);
        return id;
    }

    @Test
    void 사진과_한_줄을_올리면_확인_대기로_시작한다() throws Exception {
        String first = photo(member);
        String second = photo(member);

        submit(member, eventId, body("  순대국집  ", first, second))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("순대국집"))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.author.id").value(member.id()))
                .andExpect(jsonPath("$.photos[*].objectKey", contains(first, second)))
                .andExpect(jsonPath("$.photos[0].url", containsString(first)));

        mvc.perform(get("/api/v1/events/{id}/certifications", eventId).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[0].photos.length()").value(2));
    }

    @Test
    void 기간_밖이면_409() throws Exception {
        long ended = event(today.minusDays(5), today.minusDays(1));
        long upcoming = event(today.plusDays(1), today.plusDays(5));
        String key = photo(member);

        submit(member, ended, body("한 줄", key)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_CLOSED"));
        submit(member, upcoming, body("한 줄", key)).andExpect(jsonPath("$.code").value("EVENT_CLOSED"));
    }

    @Test
    void 시작일과_종료일_당일은_올릴_수_있다() throws Exception {
        submit(member, event(today, today), body("한 줄", photo(member))).andExpect(status().isCreated());
    }

    @Test
    void 사진이_없거나_남의_키거나_입력이_틀리면_400이고_글이_남지_않는다() throws Exception {
        String missing = "cert/2026/10/%d/%s.webp".formatted(member.id(), "a".repeat(32));
        submit(member, eventId, body("한 줄", missing)).andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
        submit(member, eventId, body("한 줄", photo(fixtures.member())))
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));
        submit(member, eventId, "{\"body\":\"한 줄\",\"photoKeys\":[]}")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        submit(member, eventId, body("  ", photo(member))).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        submit(member, eventId, body("한 줄", photo(member), photo(member), photo(member), photo(member)))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certification", Integer.class)).isZero();
    }

    @Test
    void 없는_이벤트와_인증은_404() throws Exception {
        submit(member, 999_999, body("한 줄", photo(member))).andExpect(status().isNotFound());
        resubmit(member, 999_999, "{\"body\":\"다시\"}").andExpect(status().isNotFound());
    }

    @Test
    void 다시_올리면_검토_기록을_비우고_사진을_통째로_바꾼다() throws Exception {
        long id = rejected(member);
        String replaced = photo(member);

        resubmit(member, id, "{\"body\":\"다시 올려요\",\"photoKeys\":[\"%s\"]}".formatted(replaced))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.body").value("다시 올려요"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist())
                .andExpect(jsonPath("$.photos[*].objectKey", contains(replaced)));

        assertThat(jdbc.queryForObject("""
                SELECT reviewed_by IS NULL AND reviewed_at IS NULL AND reject_reason IS NULL AND updated_at IS NOT NULL
                FROM certification WHERE id = ?
                """, Boolean.class, id)).isTrue();
    }

    @Test
    void 보낸_필드만_바꾸고_같은_사진을_다시_보낼_수_있다() throws Exception {
        long id = rejected(member);
        String key = jdbc.queryForObject("SELECT object_key FROM attachment WHERE owner_id = ?", String.class, id);

        resubmit(member, id, "{\"photoKeys\":[\"%s\"]}".formatted(key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("한 줄"))
                .andExpect(jsonPath("$.photos[*].objectKey", contains(key)));
    }

    @Test
    void 사진_교체에_실패하면_기존_사진과_반려_상태가_그대로다() throws Exception {
        long id = rejected(member);
        String key = jdbc.queryForObject("SELECT object_key FROM attachment WHERE owner_id = ?", String.class, id);

        resubmit(member, id, "{\"photoKeys\":[\"%s\"]}".formatted(photo(fixtures.member())))
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT object_key FROM attachment WHERE owner_id = ?", String.class, id))
                .isEqualTo(key);
        assertThat(jdbc.queryForObject("SELECT state FROM certification WHERE id = ?", String.class, id))
                .isEqualTo("REJECTED");
    }

    @Test
    void 남의_것이거나_반려가_아니거나_기간이_끝나면_다시_올릴_수_없다() throws Exception {
        long id = rejected(member);
        resubmit(fixtures.member(), id, "{\"body\":\"다시\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));

        long pending = submitted(member);
        resubmit(member, pending, "{\"body\":\"다시\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_NOT_REJECTED"));

        resubmit(member, id, "{}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        jdbc.update("UPDATE event SET end_date = ? WHERE id = ?", today.minusDays(1), eventId);
        resubmit(member, id, "{\"body\":\"다시\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_CLOSED"));
    }

    @Test
    void 탈퇴하면_인증_사진_연결을_끊어_배치가_지우고_글과_횟수는_남는다() throws Exception {
        long id = submitted(member);
        jdbc.update("UPDATE certification SET state = 'APPROVED' WHERE id = ?", id);
        String key = jdbc.queryForObject("SELECT object_key FROM attachment WHERE owner_id = ?", String.class, id);
        storage.put(key, 1000, Instant.now().minusSeconds(25 * 3600));

        withdrawal.erase(member.id(), "gone@test.com", Instant.now());
        cleanupJob.run();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class)).isZero();
        assertThat(storage.exists(key)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certification WHERE state = 'APPROVED'", Integer.class))
                .isEqualTo(1);
    }

}
