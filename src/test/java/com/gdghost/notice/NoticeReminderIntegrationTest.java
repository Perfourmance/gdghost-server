package com.gdghost.notice;

import static com.gdghost.support.TestCsrf.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.notification.NotificationWriter;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.gdghost.support.MutableClock;

/** 미확인 공지 재알림 · 푸시 미루기 */
@IntegrationTest
class NoticeReminderIntegrationTest {

    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakePushGateway push;

    @Autowired
    FakeDiscordGateway discord;

    @Autowired
    NoticeRepository notices;

    @Autowired
    NoticeReminderReader reader;

    @Autowired
    NotificationWriter writer;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    NoticeReminderPushJob job;

    TestMember staff;
    long notice;

    @BeforeEach
    void setUp() {
        push.clear();
        discord.clear();
        staff = fixtures.staff();
        notice = fixtures.notice(staff.id());
    }

    @Test
    void 확인하지_않은_활동_중_부원에게만_알리고_대상_수를_준다() throws Exception {
        TestMember unconfirmed = fixtures.member();
        TestMember readOnly = fixtures.member();
        TestMember confirmed = fixtures.member();
        fixtures.suspended();
        fixtures.withdrawn();
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, read_at) VALUES (?, ?, NOW(6))", notice,
                readOnly.id());
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, read_at, confirmed_at) VALUES (?, ?, NOW(6), NOW(6))",
                notice, confirmed.id());
        jdbc.update("INSERT INTO notice_read (notice_id, member_id, read_at, confirmed_at) VALUES (?, ?, NOW(6), NOW(6))",
                notice, staff.id());

        remind(notice).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targetCount").value(2))
                .andExpect(jsonPath("$.remindedAt").isNotEmpty());

        assertThat(jdbc.queryForList("SELECT member_id FROM notification WHERE type = 'NOTICE_REMIND'", Long.class))
                .containsExactlyInAnyOrder(unconfirmed.id(), readOnly.id());
        assertThat(jdbc.queryForObject("SELECT target_type FROM notification LIMIT 1", String.class))
                .isEqualTo("NOTICE");
        assertThat(jdbc.queryForObject("SELECT reminded_at IS NOT NULL FROM notice WHERE id = ?", Boolean.class, notice))
                .isTrue();
    }

    @Test
    void 공지당_한_번만_보낸다() throws Exception {
        fixtures.member();

        remind(notice).andExpect(status().isAccepted());
        remind(notice).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTICE_REMIND_USED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'NOTICE_REMIND'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void 없거나_지운_공지는_404() throws Exception {
        remind(999_999L).andExpect(status().isNotFound());
        jdbc.update("UPDATE notice SET deleted_at = NOW(6) WHERE id = ?", notice);
        remind(notice).andExpect(status().isNotFound());
    }

    @Test
    void 디스코드로는_보내지_않는다() throws Exception {
        fixtures.member();

        remind(notice).andExpect(status().isAccepted());

        assertThat(discord.after(Duration.ofMillis(200), sent -> true)).isEmpty();
    }

    @Test
    void 낮에는_푸시도_바로_간다() throws Exception {
        TestMember member = fixtures.member();
        String phone = subscribe(member);

        remindAt(Instant.parse("2026-10-30T03:00:00Z"));

        assertThat(push.await(sent -> sent.payload().contains("NOTICE_REMIND"), 1))
                .extracting(sent -> sent.target().endpoint()).containsExactly(phone);
    }

    @Test
    void 밤에는_앱_내_알림만_바로_가고_푸시는_08시_배치가_보낸다() throws Exception {
        TestMember stillUnconfirmed = fixtures.member();
        TestMember confirmedLater = fixtures.member();
        String phone = subscribe(stillUnconfirmed);
        String other = subscribe(confirmedLater);

        remindAt(Instant.parse("2026-10-30T14:00:00Z")); // KST 23:00

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE type = 'NOTICE_REMIND'", Integer.class))
                .isEqualTo(3);
        assertThat(push.after(Duration.ofMillis(300), sent -> true)).isEmpty();

        jdbc.update("INSERT INTO notice_read (notice_id, member_id, read_at, confirmed_at) VALUES (?, ?, NOW(6), NOW(6))",
                notice, confirmedLater.id());
        markRemindedThisMorning();

        job.run();

        assertThat(push.await(sent -> sent.payload().contains("NOTICE_REMIND"), 1))
                .extracting(sent -> sent.target().endpoint()).containsExactly(phone).doesNotContain(other);
        assertThat(jdbc.queryForObject("SELECT message FROM batch_log WHERE job_name = ?", String.class,
                NoticeReminderPushJob.NAME)).isEqualTo("notices=1 members=2");
    }

    @Test
    void 배치는_구간_밖의_재알림과_지운_공지는_보내지_않는다() throws Exception {
        TestMember member = fixtures.member();
        subscribe(member);
        remindAt(Instant.parse("2026-10-30T14:00:00Z"));
        jdbc.update("UPDATE notice SET reminded_at = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(3), notice);

        job.run();
        assertThat(push.after(Duration.ofMillis(100), sent -> true)).isEmpty();

        markRemindedThisMorning();
        jdbc.update("UPDATE notice SET deleted_at = NOW(6) WHERE id = ?", notice);
        job.run();
        assertThat(push.after(Duration.ofMillis(100), sent -> true)).isEmpty();
    }

    /** 운영 시계로 어젯밤에 누른 것처럼. 오늘 07시(KST)는 배치 구간 안이다 */
    private void markRemindedThisMorning() {
        LocalDateTime sevenKst = LocalDateTime.now(KST).toLocalDate().atTime(LocalTime.of(7, 0));
        jdbc.update("UPDATE notice SET reminded_at = ? WHERE id = ?",
                sevenKst.atZone(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(), notice);
    }

    /** 시각을 고정한 서비스로 누른다. 트랜잭션 안이어야 커밋 후 푸시가 나간다 */
    private void remindAt(Instant now) {
        Clock clock = new MutableClock(now);
        NoticeReminderService service = new NoticeReminderService(notices, reader, writer, clock);
        tx.executeWithoutResult(status -> service.remind(notice));
    }

    private String subscribe(TestMember member) throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        mvc.perform(post("/api/v1/me/push-subscriptions").with(csrfToken()).cookie(fixtures.loginCookie(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH
                        + "\"}}"))
                .andExpect(status().isCreated());
        return endpoint;
    }

    private ResultActions remind(long noticeId) throws Exception {
        return mvc.perform(post("/api/v1/ops/notices/{id}/reminders", noticeId).with(csrfToken())
                .cookie(fixtures.loginCookie(staff)));
    }

}
