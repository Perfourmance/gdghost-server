package com.gdghost.recruit;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.member.MemberWithdrawalWriter;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class RecruitIntegrationTest {

    private static final String URL = "/api/v1/recruits";

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecruitService service;

    @Autowired
    RecruitCloseJob closeJob;

    @Autowired
    MemberWithdrawalWriter withdrawal;

    private TestMember owner;
    private TestMember member;

    @BeforeEach
    void setUp() {
        owner = fixtures.member();
        member = fixtures.member();
    }

    private static Instant hoursLater(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    }

    private ResultActions create(TestMember who, String body) throws Exception {
        return mvc.perform(post(URL).cookie(fixtures.loginCookie(who)).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(Instant meetAt, int capacity) {
        return """
                {"kind":"TAXI","title":"  천안역 택시  ","meetAt":"%s","capacity":%d,"memo":"정문"}"""
                .formatted(meetAt, capacity);
    }

    private long open(TestMember who, int capacity) throws Exception {
        String response = create(who, body(hoursLater(5), capacity)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions join(TestMember who, long id) throws Exception {
        return mvc.perform(post(URL + "/{id}/participants", id).cookie(fixtures.loginCookie(who)).with(csrfToken()));
    }

    private ResultActions leave(TestMember who, long id) throws Exception {
        return mvc.perform(delete(URL + "/{id}/participants/me", id).cookie(fixtures.loginCookie(who))
                .with(csrfToken()));
    }

    private ResultActions close(TestMember who, long id) throws Exception {
        return mvc.perform(post(URL + "/{id}/closure", id).cookie(fixtures.loginCookie(who)).with(csrfToken()));
    }

    private ResultActions list(TestMember who) throws Exception {
        return mvc.perform(get(URL).cookie(fixtures.loginCookie(who)));
    }

    private void meetAt(long id, LocalDateTime when) {
        jdbc.update("UPDATE recruit SET meet_at = ? WHERE id = ?", when, id);
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private int count(long id) {
        return jdbc.queryForObject("SELECT joined_count FROM recruit WHERE id = ?", Integer.class, id);
    }

    @Test
    void 모집을_열면_개설자가_참여자로_들어간다() throws Exception {
        create(owner, body(hoursLater(5), 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("천안역 택시"))
                .andExpect(jsonPath("$.joinedCount").value(1))
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.author.id").value(owner.id()))
                .andExpect(jsonPath("$.memo").value("정문"));
        assertThat(jdbc.queryForObject("SELECT member_id FROM recruit_member", Long.class)).isEqualTo(owner.id());
    }

    @Test
    void 지난_시각이면_RECRUIT_TIME_PAST_90일을_넘거나_인원이_틀리면_400() throws Exception {
        create(owner, body(hoursLater(-1), 4)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECRUIT_TIME_PAST"));
        create(owner, body(hoursLater(24 * 91), 4)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(owner, body(hoursLater(5), 1)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(owner, body(hoursLater(5), 51)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 일정을_연결하고_없거나_지운_일정이면_404() throws Exception {
        long schedule = new SimpleJdbcInsert(jdbc).withTableName("schedule").usingGeneratedKeyColumns("id")
                .usingColumns("author_id", "kind", "title", "start_date")
                .executeAndReturnKey(Map.of("author_id", owner.id(), "kind", "CONTEST", "title", "해커톤",
                        "start_date", "2026-12-01"))
                .longValue();
        String linked = """
                {"kind":"CONTEST","title":"팀원 구해요","meetAt":"%s","capacity":4,"scheduleId":%d}"""
                .formatted(hoursLater(5), schedule);
        create(owner, linked).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT schedule_id FROM recruit", Long.class)).isEqualTo(schedule);

        jdbc.update("UPDATE schedule SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", schedule);
        create(owner, linked).andExpect(status().isNotFound());
    }

    @Test
    void 참여하면_늘고_정원이_차면_닫히고_더는_못_들어온다() throws Exception {
        long id = open(owner, 2);

        join(member, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.joinedCount").value(2))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.state").value("CLOSED"));

        join(fixtures.member(), id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECRUIT_FULL"));
    }

    @Test
    void 중복_참여는_409() throws Exception {
        long id = open(owner, 4);
        join(member, id).andExpect(status().isOk());

        join(member, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        join(owner, id).andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        assertThat(count(id)).isEqualTo(2);
    }

    @Test
    void 시간이_지났거나_닫힌_모집은_참여할_수_없다() throws Exception {
        long past = open(owner, 4);
        meetAt(past, utcNow().minusMinutes(1));
        join(member, past).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECRUIT_CLOSED"));

        long closed = open(owner, 4);
        close(owner, closed).andExpect(status().isOk());
        join(member, closed).andExpect(jsonPath("$.code").value("RECRUIT_CLOSED"));
    }

    @Test
    void 없는_모집은_404() throws Exception {
        mvc.perform(get(URL + "/{id}", 999_999).cookie(fixtures.loginCookie(member))).andExpect(status().isNotFound());
        join(member, 999_999).andExpect(status().isNotFound());
        leave(member, 999_999).andExpect(status().isNotFound());
        close(member, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void 동시에_참여해도_정원을_넘지_않는다() throws Exception {
        long id = open(owner, 3);
        List<TestMember> members = List.of(fixtures.member(), fixtures.member(), fixtures.member(), fixtures.member(),
                fixtures.member(), fixtures.member());
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        for (TestMember who : members) {
            tasks.add(() -> {
                try {
                    service.join(who.id(), id);
                    return null;
                } catch (BusinessException e) {
                    return e.code();
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(members.size());
        List<ErrorCode> results = new ArrayList<>();
        try {
            for (Future<ErrorCode> result : pool.invokeAll(tasks)) {
                results.add(result.get());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(results.stream().filter(code -> code == null)).hasSize(2);
        assertThat(results.stream().filter(code -> code == ErrorCode.RECRUIT_FULL)).hasSize(4);
        assertThat(count(id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruit_member WHERE recruit_id = ?", Integer.class, id))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT state FROM recruit WHERE id = ?", String.class, id)).isEqualTo("CLOSED");
    }

    @Test
    void 참여를_취소하고_다_차서_닫힌_모집은_다시_열린다() throws Exception {
        long id = open(owner, 2);
        join(member, id).andExpect(jsonPath("$.state").value("CLOSED"));

        leave(member, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false))
                .andExpect(jsonPath("$.joinedCount").value(1))
                .andExpect(jsonPath("$.state").value("OPEN"));
    }

    @Test
    void 개설자는_취소할_수_없고_참여하지_않았으면_404() throws Exception {
        long id = open(owner, 4);

        leave(owner, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECRUIT_OWNER_CANNOT_LEAVE"));
        leave(member, id).andExpect(status().isNotFound());
    }

    @Test
    void 개설자가_닫았거나_시간이_지나면_취소할_수_없다() throws Exception {
        long id = open(owner, 4);
        join(member, id).andExpect(status().isOk());
        close(owner, id).andExpect(status().isOk());
        leave(member, id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECRUIT_CLOSED"));

        long full = open(owner, 2);
        join(member, full).andExpect(status().isOk());
        meetAt(full, utcNow().minusMinutes(1));
        leave(member, full).andExpect(jsonPath("$.code").value("RECRUIT_CLOSED"));
        assertThat(jdbc.queryForObject("SELECT state FROM recruit WHERE id = ?", String.class, full))
                .isEqualTo("CLOSED");
    }

    @Test
    void 닫기는_개설자만_하고_다시_눌러도_성공한다() throws Exception {
        long id = open(owner, 4);

        close(member, id).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        close(owner, id).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));
        close(owner, id).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    void 목록은_모집중_먼저_가까운_순이고_7일_지난_것은_빠진다() throws Exception {
        long later = open(owner, 4);
        long sooner = open(owner, 4);
        long closed = open(owner, 4);
        long passedRecently = open(owner, 4);
        long passedLongAgo = open(owner, 4);
        meetAt(later, utcNow().plusHours(9));
        meetAt(sooner, utcNow().plusHours(1));
        meetAt(closed, utcNow().plusHours(2));
        meetAt(passedRecently, utcNow().minusDays(1));
        meetAt(passedLongAgo, utcNow().minusDays(8));
        close(owner, closed).andExpect(status().isOk());
        join(member, later).andExpect(status().isOk());

        list(member).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id",
                        contains((int) sooner, (int) later, (int) passedRecently, (int) closed)))
                .andExpect(jsonPath("$.items[*].state", contains("OPEN", "OPEN", "CLOSED", "CLOSED")))
                .andExpect(jsonPath("$.items[*].joined", contains(false, true, false, false)))
                .andExpect(jsonPath("$.items[0].mine").value(false))
                .andExpect(jsonPath("$.items[0].memo").value("정문"));
    }

    @Test
    void 목록은_커서로_이어지고_분류로_거른다() throws Exception {
        long first = open(owner, 4);
        long second = open(owner, 4);
        long third = open(owner, 4);
        meetAt(first, utcNow().plusHours(1));
        meetAt(second, utcNow().plusHours(2));
        meetAt(third, utcNow().plusHours(3));

        String page = mvc.perform(get(URL).param("size", "2").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[*].id", contains((int) first, (int) second)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(URL).param("size", "2").param("cursor", JsonPath.<String>read(page, "$.nextCursor"))
                .cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items[*].id", contains((int) third)))
                .andExpect(jsonPath("$.hasMore").value(false));

        mvc.perform(get(URL).param("kind", "ETC").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(URL).param("cursor", "bm90LWEtY3Vyc29y").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 상세는_참여자를_주고_마감된_모집도_열린다() throws Exception {
        long id = open(owner, 2);
        join(member, id).andExpect(status().isOk());

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.participants[*].id", contains((int) owner.id(), (int) member.id())))
                .andExpect(jsonPath("$.participants[0].nickname").value(owner.nickname()));
    }

    @Test
    void 배치는_시간이_지난_모집만_닫는다() throws Exception {
        long past = open(owner, 4);
        long future = open(owner, 4);
        meetAt(past, utcNow().minusMinutes(1));

        closeJob.run();

        assertThat(jdbc.queryForObject("SELECT state FROM recruit WHERE id = ?", String.class, past))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT state FROM recruit WHERE id = ?", String.class, future))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT message FROM batch_log WHERE job_name = 'recruit_close_10min'",
                String.class)).isEqualTo("closed=1");
    }

    @Test
    void 탈퇴하면_앞으로의_모집_자리를_비우고_연_모집을_닫고_지난_기록은_남긴다() throws Exception {
        long full = open(owner, 2);
        join(member, full).andExpect(status().isOk());
        long past = open(owner, 4);
        join(member, past).andExpect(status().isOk());
        meetAt(past, utcNow().minusDays(1));
        long own = open(member, 4);

        withdrawal.erase(member.id(), "gone@test.com", Instant.now());

        assertThat(count(full)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM recruit WHERE id = ?", String.class, full)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruit_member WHERE recruit_id = ? AND member_id = ?",
                Integer.class, full, member.id())).isZero();
        assertThat(count(past)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT state FROM recruit WHERE id = ?", String.class, own))
                .isEqualTo("CLOSED");
    }

}
