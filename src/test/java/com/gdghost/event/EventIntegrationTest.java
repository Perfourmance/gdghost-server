package com.gdghost.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class EventIntegrationTest {

    private static final String URL = "/api/v1/events";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    private TestMember staff;
    private TestMember member;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        member = fixtures.member();
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private long event(String name, LocalDate start, LocalDate end) {
        return new SimpleJdbcInsert(jdbc).withTableName("event").usingGeneratedKeyColumns("id")
                .usingColumns("created_by", "name", "start_date", "end_date")
                .executeAndReturnKey(Map.of("created_by", staff.id(), "name", name, "start_date", start,
                        "end_date", end))
                .longValue();
    }

    private long ongoing() {
        return event("진행", today.minusDays(1), today.plusDays(1));
    }

    private long cert(long eventId, long memberId, String state, String reason) {
        Map<String, Object> values = new HashMap<>();
        values.put("event_id", eventId);
        values.put("member_id", memberId);
        values.put("body", "한 줄 " + state);
        values.put("state", state);
        values.put("reject_reason", reason);
        return new SimpleJdbcInsert(jdbc).withTableName("certification").usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "member_id", "body", "state", "reject_reason")
                .executeAndReturnKey(values).longValue();
    }

    private void photo(long certId, String key) {
        jdbc.update("INSERT INTO attachment (owner_type, owner_id, object_key, sort_order) VALUES ('CERT', ?, ?, 0)",
                certId, key);
    }

    private ResultActions certs(TestMember viewer, long eventId) throws Exception {
        return mvc.perform(get(URL + "/{id}/certifications", eventId).cookie(fixtures.loginCookie(viewer)));
    }

    @Test
    void 목록은_진행_예정_지난_순이고_상태는_오늘로_계산한다() throws Exception {
        long endsLater = event("늦게 끝남", today.minusDays(1), today.plusDays(5));
        long endsSooner = event("오늘 끝남", today.minusDays(9), today);
        long upcoming = event("예정", today.plusDays(1), today.plusDays(2));
        long oldEnded = event("오래전", today.minusDays(9), today.minusDays(5));
        long ended = event("어제 끝남", today.minusDays(3), today.minusDays(1));

        mvc.perform(get(URL).cookie(fixtures.loginCookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) endsSooner, (int) endsLater, (int) upcoming,
                        (int) ended, (int) oldEnded)))
                .andExpect(jsonPath("$.items[*].state",
                        contains("ONGOING", "ONGOING", "UPCOMING", "ENDED", "ENDED")));

        mvc.perform(get(URL).param("state", "UPCOMING").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[*].id", contains((int) upcoming)));
    }

    @Test
    void 목록은_참여자와_내_횟수를_센다() throws Exception {
        long id = ongoing();
        TestMember other = fixtures.member();
        cert(id, member.id(), "APPROVED", null);
        cert(id, member.id(), "APPROVED", null);
        cert(id, member.id(), "PENDING", null);
        cert(id, other.id(), "APPROVED", null);
        cert(id, other.id(), "REJECTED", "흐려요");

        mvc.perform(get(URL).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[0].participantCount").value(2))
                .andExpect(jsonPath("$.items[0].approvedCount").value(3))
                .andExpect(jsonPath("$.items[0].my.approvedCount").value(2))
                .andExpect(jsonPath("$.items[0].my.pendingCount").value(1));
    }

    @Test
    void 인증이_없는_이벤트는_0으로_센다() throws Exception {
        ongoing();

        mvc.perform(get(URL).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[0].participantCount").value(0))
                .andExpect(jsonPath("$.items[0].approvedCount").value(0))
                .andExpect(jsonPath("$.items[0].my.pendingCount").value(0));
    }

    @Test
    void 상세는_안내와_내_현황을_주고_대기_수는_운영진에게만_준다() throws Exception {
        long id = ongoing();
        jdbc.update("UPDATE event SET description = '설명', requirement = '사진', guide_steps = ? WHERE id = ?",
                "[\"간다\",\"찍는다\"]", id);
        cert(id, member.id(), "APPROVED", null);
        cert(id, member.id(), "PENDING", null);
        long rejected = cert(id, member.id(), "REJECTED", "흐려요");
        cert(id, fixtures.member().id(), "PENDING", null);

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("설명"))
                .andExpect(jsonPath("$.guideSteps", contains("간다", "찍는다")))
                .andExpect(jsonPath("$.state").value("ONGOING"))
                .andExpect(jsonPath("$.my.approvedCount").value(1))
                .andExpect(jsonPath("$.my.pendingCount").value(1))
                .andExpect(jsonPath("$.my.rejected[0].id").value(rejected))
                .andExpect(jsonPath("$.my.rejected[0].rejectReason").value("흐려요"))
                .andExpect(jsonPath("$.pendingCount").doesNotExist());

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(staff)))
                .andExpect(jsonPath("$.pendingCount").value(2));
        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(fixtures.dev())))
                .andExpect(jsonPath("$.pendingCount").doesNotExist());
    }

    @Test
    void 안내가_없으면_빈_배열이고_없는_이벤트는_404() throws Exception {
        long id = ongoing();

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.guideSteps").isEmpty());
        mvc.perform(get(URL + "/{id}", 999_999).cookie(fixtures.loginCookie(member)))
                .andExpect(status().isNotFound());
        certs(member, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void 순위는_인증_횟수순이고_같으면_같은_순위이며_정지_탈퇴는_뺀다() throws Exception {
        long id = ongoing();
        TestMember second = fixtures.member();
        TestMember third = fixtures.member();
        TestMember suspended = fixtures.suspended();
        TestMember withdrawn = fixtures.withdrawn();
        for (int i = 0; i < 3; i++) {
            cert(id, member.id(), "APPROVED", null);
            cert(id, suspended.id(), "APPROVED", null);
            cert(id, withdrawn.id(), "APPROVED", null);
        }
        cert(id, second.id(), "APPROVED", null);
        cert(id, third.id(), "APPROVED", null);
        cert(id, third.id(), "PENDING", null);

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.ranking[*].member.id",
                        contains((int) member.id(), (int) second.id(), (int) third.id())))
                .andExpect(jsonPath("$.ranking[*].rank", contains(1, 2, 2)))
                .andExpect(jsonPath("$.ranking[*].approvedCount", contains(3, 1, 1)));
    }

    @Test
    void 순위는_상위_10명만_준다() throws Exception {
        long id = ongoing();
        for (int i = 0; i < 11; i++) {
            cert(id, fixtures.member().id(), "APPROVED", null);
        }

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.ranking.length()").value(10));
    }

    @Test
    void 부원에게는_인증된_글과_내_글만_주고_남의_대기_반려_사진_주소는_나가지_않는다() throws Exception {
        long id = ongoing();
        TestMember other = fixtures.member();
        long approved = cert(id, other.id(), "APPROVED", null);
        long hiddenPending = cert(id, other.id(), "PENDING", null);
        long hiddenRejected = cert(id, other.id(), "REJECTED", "남의 사유");
        long myRejected = cert(id, member.id(), "REJECTED", "내 사유");
        photo(approved, "cert/2026/10/1/approved.webp");
        photo(hiddenPending, "cert/2026/10/1/secret-pending.webp");
        photo(hiddenRejected, "cert/2026/10/1/secret-rejected.webp");

        String body = certs(member, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) myRejected, (int) approved)))
                .andExpect(jsonPath("$.items[0].mine").value(true))
                .andExpect(jsonPath("$.items[0].rejectReason").value("내 사유"))
                .andExpect(jsonPath("$.items[1].author.id").value(other.id()))
                .andExpect(jsonPath("$.items[1].photos[0].url", containsString("approved.webp")))
                .andExpect(jsonPath("$.items[1].photos[0].urlExpiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-pending", "secret-rejected", "남의 사유");
    }

    @Test
    void 운영진은_전부_보고_사유도_본다() throws Exception {
        long id = ongoing();
        long pending = cert(id, member.id(), "PENDING", null);
        long rejected = cert(id, member.id(), "REJECTED", "흐려요");
        photo(pending, "cert/2026/10/1/pending.webp");

        certs(staff, id)
                .andExpect(jsonPath("$.items[*].id", contains((int) rejected, (int) pending)))
                .andExpect(jsonPath("$.items[0].rejectReason").value("흐려요"))
                .andExpect(jsonPath("$.items[1].photos[0].url", containsString("pending.webp")));
    }

    @Test
    void 개발자는_부원과_같게_본다() throws Exception {
        long id = ongoing();
        cert(id, member.id(), "PENDING", null);

        certs(fixtures.dev(), id).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 탈퇴한_작성자로_표시하고_커서로_이어진다() throws Exception {
        long id = ongoing();
        long old = cert(id, fixtures.withdrawn().id(), "APPROVED", null);
        long recent = cert(id, member.id(), "APPROVED", null);

        String page = mvc.perform(get(URL + "/{id}/certifications", id).param("size", "1")
                .cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[*].id", contains((int) recent)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get(URL + "/{id}/certifications", id).param("size", "1")
                .param("cursor", JsonPath.<String>read(page, "$.nextCursor")).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[*].id", contains((int) old)))
                .andExpect(jsonPath("$.items[0].author.withdrawn").value(true))
                .andExpect(jsonPath("$.items[0].author.id").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(false));

        mvc.perform(get(URL + "/{id}/certifications", id).param("cursor", "YWJj").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

}
