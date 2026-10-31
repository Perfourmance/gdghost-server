package com.gdghost.schedule;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.idempotency.IdempotencyAspect;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

import java.util.Map;

@IntegrationTest
class ScheduleIntegrationTest {

    private static final String URL = "/api/v1/schedules";
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
    DeadlineJob job;

    @Autowired
    FakeDiscordGateway discord;

    @Autowired
    FakePushGateway push;

    private TestMember member;
    private TestMember staff;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        member = fixtures.member();
        staff = fixtures.staff();
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        discord.clear();
        push.clear();
    }

    private long schedule(long authorId, String kind, String title, LocalDate start, LocalDate end) {
        return new SimpleJdbcInsert(jdbc).withTableName("schedule").usingGeneratedKeyColumns("id")
                .usingColumns("author_id", "kind", "is_official", "title", "start_date", "end_date")
                .executeAndReturnKey(Map.of("author_id", authorId, "kind", kind,
                        "is_official", kind.equals("CLUB") || kind.equals("ACADEMIC"), "title", title,
                        "start_date", start, "end_date", end == null ? start : end))
                .longValue();
    }

    private long single(long authorId, String kind, String title, LocalDate date) {
        long id = schedule(authorId, kind, title, date, null);
        jdbc.update("UPDATE schedule SET end_date = NULL WHERE id = ?", id);
        return id;
    }

    private long recruit(long authorId, long scheduleId, int joined, int capacity, LocalDateTime meetAt) {
        return new SimpleJdbcInsert(jdbc).withTableName("recruit").usingGeneratedKeyColumns("id")
                .usingColumns("author_id", "schedule_id", "kind", "title", "meet_at", "capacity", "joined_count")
                .executeAndReturnKey(Map.of("author_id", authorId, "schedule_id", scheduleId, "kind", "CONTEST",
                        "title", "팀원 구해요", "meet_at", meetAt, "capacity", capacity, "joined_count", joined))
                .longValue();
    }

    private ResultActions create(TestMember who, String body) throws Exception {
        return mvc.perform(post(URL).cookie(fixtures.loginCookie(who)).with(csrfToken())
                .header(IdempotencyAspect.HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(TestMember who, long id, String body) throws Exception {
        return mvc.perform(patch(URL + "/{id}", id).cookie(fixtures.loginCookie(who)).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions range(LocalDate from, LocalDate to) throws Exception {
        return mvc.perform(get(URL).cookie(fixtures.loginCookie(member))
                .param("from", from.toString()).param("to", to.toString()));
    }

    @Test
    void 기간이_걸치는_일정을_시작일_순으로_준다() throws Exception {
        LocalDate from = today.plusDays(10);
        LocalDate to = today.plusDays(20);
        long spanning = schedule(member.id(), "CONTEST", "걸침", today.plusDays(5), today.plusDays(12));
        long inside = single(member.id(), "JOB", "안쪽", today.plusDays(15));
        single(member.id(), "JOB", "앞", today.plusDays(9));
        single(member.id(), "JOB", "뒤", today.plusDays(21));
        long deleted = single(member.id(), "JOB", "지움", today.plusDays(15));
        jdbc.update("UPDATE schedule SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deleted);

        range(from, to).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", contains((int) spanning, (int) inside)))
                .andExpect(jsonPath("$.items[0].author.id").value(member.id()))
                .andExpect(jsonPath("$.items[0].author.nickname").value(member.nickname()));
    }

    @Test
    void 분류로_거른다() throws Exception {
        single(member.id(), "JOB", "채용", today);
        long contest = single(member.id(), "CONTEST", "공모전", today);

        mvc.perform(get(URL).cookie(fixtures.loginCookie(member)).param("from", today.toString())
                .param("to", today.toString()).param("kind", "CONTEST"))
                .andExpect(jsonPath("$.items[*].id", contains((int) contest)));
    }

    @Test
    void dDay와_마감_임박은_종료일_기준이다() throws Exception {
        schedule(member.id(), "CONTEST", "기간", today.minusDays(5), today.plusDays(3));
        long late = single(member.id(), "JOB", "멀리", today.plusDays(4));
        long past = single(member.id(), "JOB", "지남", today.minusDays(1));

        range(today.minusDays(5), today.plusDays(5))
                .andExpect(jsonPath("$.items[0].dDay").value(3))
                .andExpect(jsonPath("$.items[0].deadlineSoon").value(true))
                .andExpect(jsonPath("$.items[?(@.id == %d)].deadlineSoon".formatted(late), contains(false)))
                .andExpect(jsonPath("$.items[?(@.id == %d)].dDay".formatted(past), contains(-1)))
                .andExpect(jsonPath("$.items[?(@.id == %d)].deadlineSoon".formatted(past), contains(false)));
    }

    @Test
    void 비회원은_제목과_날짜까지만_받는다() throws Exception {
        single(member.id(), "CLUB", "설명회", today);
        jdbc.update("UPDATE schedule SET place = '세미나실', memo = '메모'");

        mvc.perform(get(URL).param("from", today.toString()).param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("설명회"))
                .andExpect(jsonPath("$.items[0].place").doesNotExist())
                .andExpect(jsonPath("$.items[0].memo").doesNotExist())
                .andExpect(jsonPath("$.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.items[0].dDay").doesNotExist());
    }

    @Test
    void 기간이_없거나_62일을_넘거나_뒤집히면_400() throws Exception {
        mvc.perform(get(URL).cookie(fixtures.loginCookie(member)).param("from", today.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        range(today, today.plusDays(62)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        range(today, today.plusDays(61)).andExpect(status().isOk());
        range(today, today.minusDays(1)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 부원은_공모전을_등록하고_상세와_같은_모양을_받는다() throws Exception {
        create(member, """
                {"kind":"CONTEST","title":"  해커톤 마감  ","startDate":"2026-12-01","endDate":"2026-12-01",
                 "place":"포털","memo":"3인 1팀"}""")
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("해커톤 마감"))
                .andExpect(jsonPath("$.isOfficial").value(false))
                .andExpect(jsonPath("$.memo").value("3인 1팀"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.relatedRecruits").isEmpty());
    }

    @Test
    void 운영진의_동아리_일정은_공식이고_부원과_개발자는_403() throws Exception {
        String club = """
                {"kind":"CLUB","title":"정기 세션","startDate":"2026-12-01"}""";
        create(staff, club).andExpect(status().isCreated()).andExpect(jsonPath("$.isOfficial").value(true));
        create(member, club).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        create(fixtures.dev(), club).andExpect(status().isForbidden());
    }

    @Test
    void 등록_입력이_틀리면_400() throws Exception {
        create(member, """
                {"kind":"JOB","title":"채","startDate":"2026-12-01"}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(member, """
                {"kind":"JOB","title":"채용 공고","startDate":"2026-12-02","endDate":"2026-12-01"}""")
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 상세는_메모와_연결된_모집을_주고_닫힘을_계산한다() throws Exception {
        long id = single(member.id(), "CONTEST", "해커톤", today);
        LocalDateTime future = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);
        long open = recruit(member.id(), id, 1, 4, future);
        long full = recruit(member.id(), id, 4, 4, future);
        long past = recruit(member.id(), id, 1, 4, LocalDateTime.now(ZoneOffset.UTC).minusHours(1));

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.relatedRecruits[*].id", contains((int) open, (int) full, (int) past)))
                .andExpect(jsonPath("$.relatedRecruits[*].state", contains("OPEN", "CLOSED", "CLOSED")));
    }

    @Test
    void 탈퇴한_작성자는_닉네임이_없다() throws Exception {
        long id = single(fixtures.withdrawn().id(), "JOB", "채용", today);

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.author.nickname").doesNotExist());
    }

    @Test
    void 지웠거나_없으면_404() throws Exception {
        long id = single(member.id(), "JOB", "채용", today);
        jdbc.update("UPDATE schedule SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", id);

        mvc.perform(get(URL + "/{id}", id).cookie(fixtures.loginCookie(member))).andExpect(status().isNotFound());
        update(fixtures.member(), id, "{\"title\":\"바꾼 제목\"}").andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/{id}", id).cookie(fixtures.loginCookie(member)).with(csrfToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 보낸_필드만_바꾸고_빈_값과_플래그로_지운다() throws Exception {
        long id = schedule(member.id(), "JOB", "채용", today, today.plusDays(1));
        jdbc.update("UPDATE schedule SET place = '온라인', memo = '메모' WHERE id = ?", id);

        update(member, id, "{\"title\":\"새 제목\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.place").value("온라인"))
                .andExpect(jsonPath("$.endDate").value(today.plusDays(1).toString()));
        assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM schedule WHERE id = ?", Boolean.class, id))
                .isTrue();

        update(member, id, "{\"place\":\"\",\"clearEndDate\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.place").doesNotExist())
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.memo").value("메모"));
    }

    @Test
    void 바뀐_것이_없으면_수정_시각을_남기지_않는다() throws Exception {
        long id = single(member.id(), "JOB", "채용", today);

        update(member, id, "{\"title\":\"채용\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT updated_at IS NULL FROM schedule WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void 수정_권한과_분류와_기간을_검사한다() throws Exception {
        long id = single(member.id(), "JOB", "채용", today);

        update(fixtures.member(), id, "{\"title\":\"남의 글\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        update(member, id, "{\"kind\":\"CLUB\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        update(member, id, "{\"endDate\":\"%s\"}".formatted(today.minusDays(1)))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        update(member, id, "{}").andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        update(staff, id, "{\"kind\":\"ACADEMIC\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.isOfficial").value(true));
    }

    @Test
    void 삭제는_작성자나_운영진이_하고_모집은_남긴다() throws Exception {
        long id = single(member.id(), "CONTEST", "해커톤", today);
        long kept = recruit(member.id(), id, 1, 4, LocalDateTime.now(ZoneOffset.UTC).plusDays(1));

        mvc.perform(delete(URL + "/{id}", id).cookie(fixtures.loginCookie(fixtures.member())).with(csrfToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        mvc.perform(delete(URL + "/{id}", id).cookie(fixtures.loginCookie(staff)).with(csrfToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM schedule WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT schedule_id IS NULL FROM recruit WHERE id = ?", Boolean.class, kept))
                .isTrue();
    }

    @Test
    void 홈은_마감이_가까운_일정_3개를_준다() throws Exception {
        single(member.id(), "JOB", "지남", today.minusDays(1));
        long second = single(member.id(), "JOB", "둘째", today.plusDays(2));
        long first = schedule(member.id(), "CONTEST", "첫째", today.minusDays(3), today.plusDays(1));
        long third = single(member.id(), "JOB", "셋째", today.plusDays(3));
        single(member.id(), "JOB", "넷째", today.plusDays(4));

        mvc.perform(get("/api/v1/home").cookie(fixtures.loginCookie(member)))
                .andExpect(jsonPath("$.upcomingSchedules[*].id", contains((int) first, (int) second, (int) third)))
                .andExpect(jsonPath("$.upcomingSchedules[0].deadlineSoon").value(true));
        mvc.perform(get("/api/v1/home"))
                .andExpect(jsonPath("$.upcomingSchedules[*].id", contains((int) first, (int) second, (int) third)))
                .andExpect(jsonPath("$.upcomingSchedules[0].deadlineSoon").doesNotExist());
    }

    private void subscribe(TestMember who) throws Exception {
        mvc.perform(post("/api/v1/me/push-subscriptions").with(csrfToken()).cookie(fixtures.loginCookie(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID()
                        + "\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH + "\"}}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 배치는_D3_D1만_디스코드에_묶고_모집_참여자에게_알린다() throws Exception {
        long d3 = single(member.id(), "CONTEST", "삼일*전", today.plusDays(3));
        long d1 = schedule(member.id(), "JOB", "하루전", today.minusDays(2), today.plusDays(1));
        single(member.id(), "JOB", "이틀전", today.plusDays(2));
        long startsSoonButLong = schedule(member.id(), "JOB", "시작만 임박", today.plusDays(1), today.plusDays(9));
        long recruit = recruit(member.id(), d3, 1, 4, LocalDateTime.now(ZoneOffset.UTC).plusDays(2));
        jdbc.update("INSERT INTO recruit_member (recruit_id, member_id) VALUES (?, ?)", recruit, member.id());
        TestMember outsider = fixtures.member();
        subscribe(member);

        job.run();

        var sent = discord.await(message -> message.channel() == DiscordChannel.MEMBER
                && message.message().title().equals("마감 임박 일정 2건"));
        assertThat(sent.message().description())
                .contains("D-1 하루전", "D-3 삼일\\*전", "/schedules/" + d1, "/schedules/" + d3)
                .doesNotContain("이틀전", "/schedules/" + startsSoonButLong);
        assertThat(jdbc.queryForList("""
                SELECT member_id FROM notification WHERE type = 'DEADLINE' AND target_type = 'SCHEDULE' AND target_id = ?
                """, Long.class, d3)).containsExactly(member.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id = ?", Integer.class,
                outsider.id())).isZero();
        assertThat(push.await(pushed -> pushed.payload().contains("DEADLINE"), 1)).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT message FROM batch_log WHERE job_name = 'deadline_daily_09' AND state = 'SUCCESS'
                """, String.class)).isEqualTo("due=2 recruits=0 notified=1");
    }

    @Test
    void 배치는_만나는_날이_D3_D1인_모집의_참여자에게_알린다() {
        long soon = recruit(member.id(), meetingIn(1));
        long later = recruit(staff.id(), meetingIn(3));
        long notYet = recruit(member.id(), meetingIn(2));

        job.run();

        assertThat(jdbc.queryForList("""
                SELECT target_id FROM notification WHERE type = 'DEADLINE' AND target_type = 'RECRUIT' ORDER BY target_id
                """, Long.class)).containsExactly(soon, later).doesNotContain(notYet);
        assertThat(jdbc.queryForObject("SELECT title FROM notification WHERE target_id = ?", String.class, soon))
                .isEqualTo("모집 D-1");
    }

    private LocalDateTime meetingIn(int days) {
        return LocalDateTime.ofInstant(today.plusDays(days).atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneOffset.UTC);
    }

    private long recruit(long authorId, LocalDateTime meetAt) {
        long id = new SimpleJdbcInsert(jdbc).withTableName("recruit").usingGeneratedKeyColumns("id")
                .usingColumns("author_id", "kind", "title", "meet_at", "capacity", "joined_count")
                .executeAndReturnKey(Map.of("author_id", authorId, "kind", "TAXI", "title", "택시", "meet_at", meetAt,
                        "capacity", 4, "joined_count", 1))
                .longValue();
        jdbc.update("INSERT INTO recruit_member (recruit_id, member_id) VALUES (?, ?)", id, authorId);
        return id;
    }

    @Test
    void 지운_일정은_배치에서_빠지고_대상이_없으면_보내지_않는다() {
        long id = single(member.id(), "JOB", "지움", today.plusDays(1));
        jdbc.update("UPDATE schedule SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", id);

        job.run();

        assertThat(discord.after(java.time.Duration.ofMillis(300),
                message -> message.message().title().startsWith("마감 임박"))).isEmpty();
    }

}
