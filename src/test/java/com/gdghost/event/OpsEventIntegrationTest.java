package com.gdghost.event;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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

@IntegrationTest
class OpsEventIntegrationTest {

    private static final String URL = "/api/v1/ops/events";
    private static final String STEPS = "[\"간다\",\"찍는다\",\"적는다\",\"올린다\"]";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeDiscordGateway discord;

    private TestMember staff;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        staff = fixtures.staff();
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        discord.clear();
    }

    private String body(String name, LocalDate start, LocalDate end, String steps) {
        return """
                {"name":"%s","startDate":"%s","endDate":"%s","description":"맛집 공유",
                 "requirement":"가게 사진","guideSteps":%s}""".formatted(name, start, end, steps);
    }

    private ResultActions create(TestMember who, String body) throws Exception {
        return mvc.perform(post(URL).cookie(fixtures.loginCookie(who)).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mvc.perform(patch(URL + "/{id}", id).cookie(fixtures.loginCookie(staff)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long event(LocalDate start, LocalDate end) throws Exception {
        String response = create(staff, body("맛집 챌린지", start, end, STEPS)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long ongoing() throws Exception {
        return event(today, today.plusDays(7));
    }

    @Test
    void 만들면_상세_모양을_주고_부원이_볼_수_있으며_알림은_없다() throws Exception {
        String response = create(staff, body("  맛집 챌린지  ", today, today.plusDays(7), STEPS))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("맛집 챌린지"))
                .andExpect(jsonPath("$.state").value("ONGOING"))
                .andExpect(jsonPath("$.guideSteps", contains("간다", "찍는다", "적는다", "올린다")))
                .andExpect(jsonPath("$.my.approvedCount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        mvc.perform(get("/api/v1/events/{id}", id).cookie(fixtures.loginCookie(fixtures.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirement").value("가게 사진"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class)).isZero();
    }

    @Test
    void 입력이_틀리면_400() throws Exception {
        create(staff, body("맛집", today, today.minusDays(1), STEPS))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(staff, body("맛집", today, today, "[\"하나\",\"둘\",\"셋\"]"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(staff, body("맛집", today, today, "[\"하나\",\"둘\",\"셋\",\" \"]"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(staff, body("맛", today, today, STEPS)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 부원과_개발자는_403() throws Exception {
        String body = body("맛집", today, today, STEPS);
        create(fixtures.member(), body).andExpect(status().isForbidden());
        create(fixtures.dev(), body).andExpect(status().isForbidden());
    }

    @Test
    void 보낸_필드만_바꾸고_기간을_연장한다() throws Exception {
        long id = ongoing();

        update(id, "{\"endDate\":\"%s\"}".formatted(today.plusDays(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").value(today.plusDays(30).toString()))
                .andExpect(jsonPath("$.name").value("맛집 챌린지"))
                .andExpect(jsonPath("$.guideSteps[0]").value("간다"));
        assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM event WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void 바뀐_것이_없으면_수정_시각을_남기지_않는다() throws Exception {
        long id = ongoing();

        update(id, "{\"name\":\"맛집 챌린지\",\"guideSteps\":%s}".formatted(STEPS)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT updated_at IS NULL FROM event WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void 수정_입력을_검사하고_없으면_404() throws Exception {
        long id = ongoing();

        update(id, "{\"endDate\":\"%s\"}".formatted(today.minusDays(1)))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        update(id, "{}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        update(id, "{\"description\":\"  \"}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        update(id, "{\"guideSteps\":[\"하나\"]}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        update(999_999, "{\"name\":\"없는 이벤트\"}").andExpect(status().isNotFound());
    }

    private ResultActions publish(long id, String body) throws Exception {
        var request = post(URL + "/{id}/notice", id).cookie(fixtures.loginCookie(staff)).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString());
        return mvc.perform(body == null ? request
                : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 공지로_발행하면_안내와_링크가_담기고_공지_작성처럼_알린다() throws Exception {
        long id = ongoing();
        fixtures.member();

        publish(id, "{\"isMustRead\":true}")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("[이벤트] 맛집 챌린지"))
                .andExpect(jsonPath("$.body", containsString("맛집 공유")))
                .andExpect(jsonPath("$.body", containsString("인증에 필요한 것: 가게 사진")))
                .andExpect(jsonPath("$.body", containsString("1. 간다\n2. 찍는다\n3. 적는다\n4. 올린다")))
                .andExpect(jsonPath("$.body", containsString("/events/" + id)))
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.isMustRead").value(true))
                .andExpect(jsonPath("$.dueDate").value(today.plusDays(7).toString()));

        discord.await(sent -> sent.is(DiscordChannel.MEMBER, "[필독] 새 공지"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'NOTICE'", Integer.class))
                .isPositive();
    }

    @Test
    void 본문_없이_발행하면_고정_필독_아님이고_여러_번_발행할_수_있다() throws Exception {
        long id = ongoing();

        publish(id, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.isMustRead").value(false));
        publish(id, "{\"isPinned\":false}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPinned").value(false));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notice", Integer.class)).isEqualTo(2);
    }

    @Test
    void 끝난_이벤트는_발행할_수_없고_없으면_404() throws Exception {
        long ended = event(today.minusDays(5), today.minusDays(1));

        publish(ended, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        publish(999_999, null).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notice", Integer.class)).isZero();
    }

}
