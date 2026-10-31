package com.gdghost.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.batch.BatchRunner;
import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.embedding.AiLogWriter;
import com.gdghost.embedding.EmbeddingService;
import com.gdghost.embedding.EmbeddingStore;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationWriter;
import com.gdghost.question.AnswerCreateRequest;
import com.gdghost.question.AnswerService;
import com.gdghost.question.DirectDelivery;
import com.gdghost.question.Question;
import com.gdghost.question.QuestionCategory;
import com.gdghost.question.QuestionCreateRequest;
import com.gdghost.question.QuestionRepository;
import com.gdghost.question.QuestionService;
import com.gdghost.support.FakeDiscordGateway;
import com.gdghost.support.FakeEmbeddingClient;
import com.gdghost.support.FakePushGateway;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.gdghost.support.MutableClock;

import tools.jackson.databind.ObjectMapper;

@IntegrationTest
class RoutingIntegrationTest {

    private static final String MODEL = "@cf/baai/bge-m3";
    private static final Instant DAY = Instant.parse("2026-10-31T03:00:00Z"); // KST 12시
    private static final Instant NIGHT = Instant.parse("2026-10-31T14:00:00Z"); // KST 23시
    private static final Instant MORNING = Instant.parse("2026-10-31T23:00:00Z"); // KST 08시

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeEmbeddingClient client;

    @Autowired
    FakeDiscordGateway discordGateway;

    @Autowired
    FakePushGateway pushGateway;

    @Autowired
    EmbeddingStore embeddings;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    QuestionService questionService;

    @Autowired
    AnswerService answerService;

    @Autowired
    MemberRepository members;

    @Autowired
    QuestionRepository questions;

    @Autowired
    RoutingReader reader;

    @Autowired
    RoutingWriter writer;

    @Autowired
    MatchingConfig config;

    @Autowired
    NotificationWriter notifications;

    @Autowired
    DiscordNotifier discord;

    @Autowired
    AiLogWriter aiLog;

    @Autowired
    BatchRunner batch;

    @Autowired
    ObjectMapper json;

    @Autowired
    TransactionTemplate transaction;

    private TestMember author;

    @BeforeEach
    void setUp() {
        client.clear();
        discordGateway.clear();
        pushGateway.clear();
        author = fixtures.member();
    }

    @Test
    void 질문을_쓰면_임베딩만_하고_후보는_만들지_않는다() {
        candidate("인턴");

        long question = questionService.create(author.id(),
                new QuestionCreateRequest(QuestionCategory.JOB, "이력서 고민", null, false, null)).question().id();

        FakeEmbeddingClient.await(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM question_embedding WHERE question_id = ?", Integer.class, question) == 1);
        assertThat(rows(question)).isEmpty();
    }

    @Test
    void 세_시간이_지나면_후보를_계산해_바로_보낸다() {
        TestMember tagged = candidate("인턴");
        embeddings.saveMember(tagged.id(), vector(0.5f), MODEL, "hash");
        TestMember similar = fixtures.member();
        embeddings.saveMember(similar.id(), vector(0.5f), MODEL, "hash");
        fixtures.member(); // 벡터도 태그도 없으면 최소 점수에 못 미친다
        long question = question(hours(3));
        embeddings.saveQuestion(question, vector(0.5f), MODEL);

        route(DAY);

        List<Map<String, Object>> rows = memberRows(question);
        assertThat(rows).extracting(row -> row.get("member_id")).containsExactly(tagged.id(), similar.id());
        assertThat(rows.getFirst().get("stage")).isEqualTo("MEMBER");
        assertThat(rows.getFirst().get("state")).isEqualTo("SENT");
        assertThat(rows.getFirst().get("sent_at")).isEqualTo(LocalDateTime.ofInstant(DAY, ZoneOffset.UTC));
        var detail = json.readTree((String) rows.getFirst().get("score_detail"));
        assertThat(detail.path("version").asString()).isEqualTo("2026-10-v1");
        assertThat(detail.path("reasons").get(0).asString()).isEqualTo("경험: 인턴");
        assertThat(routedNotifications(tagged.id(), question)).isEqualTo(1);
        assertThat(batchMessage()).isEqualTo("sent=2 staff=0");
    }

    @Test
    void 세_시간이_안_됐으면_보내지_않고_다시_돌려도_두_번_보내지_않는다() {
        candidate("인턴");
        long early = question(hours(3).minusMinutes(1));
        long due = question(hours(3));

        route(DAY);
        route(DAY);

        assertThat(memberRows(early)).isEmpty();
        assertThat(memberRows(due)).hasSize(1);
    }

    @Test
    void 밤에는_쉬고_08시에_보낸다() {
        TestMember tagged = candidate("인턴");
        long question = question(NIGHT.minus(Duration.ofHours(3)));

        route(NIGHT);
        assertThat(rows(question)).isEmpty();
        assertThat(batchMessage()).isEqualTo("quiet");

        route(MORNING);
        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(tagged.id());
    }

    @Test
    void 받을_수_없는_사람은_뺀다() {
        TestMember off = candidate("인턴");
        jdbc.update("UPDATE member SET accept_question = FALSE WHERE id = ?", off.id());
        TestMember hidden = candidate("인턴");
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());
        TestMember suspended = fixtures.suspended();
        tag(suspended.id(), "인턴");
        TestMember full = candidate("인턴");
        for (int i = 0; i < 3; i++) {
            long old = question(hours(100));
            sent(old, full.id(), DAY.minus(Duration.ofHours(1)));
        }
        TestMember ok = candidate("인턴");
        tag(author.id(), "인턴");
        long question = question(hours(3));

        route(DAY);

        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(ok.id());
    }

    @Test
    void 어제_받은_것은_하루_3건에_세지_않는다() {
        TestMember member = candidate("인턴");
        for (int i = 0; i < 3; i++) {
            sent(question(hours(100)), member.id(), DAY.minus(Duration.ofDays(1)));
        }
        fixtures.member();
        // 연속 지목에 걸리지 않게 그 뒤에 다른 사람이 받은 질문을 둔다
        sent(question(hours(100)), fixtures.member().id(), DAY.minus(Duration.ofDays(1)));
        long question = question(hours(3));

        route(DAY);

        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(member.id());
    }

    @Test
    void 한_회차_안에서도_앞_질문에서_받은_사람은_다음_질문에_연달아_가지_않는다() {
        TestMember first = candidate("인턴");
        TestMember second = candidate("인턴");
        TestMember third = candidate("인턴");
        TestMember fourth = candidate("인턴");
        long older = question(hours(4));
        long newer = question(hours(3));

        route(DAY);

        assertThat(memberRows(older)).extracting(row -> row.get("member_id"))
                .containsExactly(first.id(), second.id(), third.id());
        assertThat(memberRows(newer)).extracting(row -> row.get("member_id")).containsExactly(fourth.id());
    }

    @Test
    void 벡터가_없으면_규칙_점수로_보내고_보냈을_때만_FALLBACK을_남긴다() {
        long none = question(hours(3));
        route(DAY);
        assertThat(fallbacks(none)).isZero();

        TestMember tagged = candidate("인턴");
        TestMember similar = fixtures.member();
        embeddings.saveMember(similar.id(), vector(0.5f), MODEL, "hash");
        route(DAY.plus(Duration.ofMinutes(10)));

        assertThat(memberRows(none)).extracting(row -> row.get("member_id")).containsExactly(tagged.id());
        assertThat(fallbacks(none)).isEqualTo(1);
    }

    @Test
    void 나중에_채워진_벡터는_보낼_때_반영된다() {
        TestMember similar = fixtures.member();
        embeddings.saveMember(similar.id(), vector(0.5f), MODEL, "hash");
        long question = question(hours(3));
        route(DAY);
        assertThat(memberRows(question)).isEmpty();

        embeddings.saveQuestion(question, vector(0.5f), MODEL);
        route(DAY.plus(Duration.ofMinutes(10)));

        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(similar.id());
        assertThat(fallbacks(question)).isZero();
    }

    @Test
    void 답이_달렸거나_지웠거나_3일이_지난_질문은_보내지_않는다() {
        candidate("인턴");
        long answered = question(hours(3));
        jdbc.update("UPDATE question SET state = 'ANSWERED' WHERE id = ?", answered);
        long deleted = question(hours(3));
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", deleted);
        long stale = question(Duration.ofDays(3).plusMinutes(1));

        route(DAY);

        assertThat(rows(answered)).isEmpty();
        assertThat(rows(deleted)).isEmpty();
        assertThat(rows(stale)).isEmpty();
    }

    @Test
    void 한_시간이_지나면_운영진_채널에_한_번만_알리고_이름을_드러내지_않는다() throws Exception {
        TestMember staff = fixtures.staff();
        long question = question(hours(1));
        String title = jdbc.queryForObject("SELECT title FROM question WHERE id = ?", String.class, question);

        route(DAY);
        route(DAY.plus(Duration.ofMinutes(10)));

        var sent = discordGateway.await(message -> message.is(DiscordChannel.STAFF, "1시간 미답변 · 취업·채용"));
        assertThat(sent.message().url()).endsWith("/questions/" + question);
        assertThat(sent.message().description()).contains(title.split(" ")[0]);
        assertThat(discordGateway.after(Duration.ofMillis(200),
                message -> message.is(DiscordChannel.STAFF, "1시간 미답변 · 취업·채용"))).hasSize(1);
        assertThat(rows(question)).hasSize(1);
        assertThat(rows(question).getFirst().get("stage")).isEqualTo("STAFF");
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.deliveries[0].stage").value("STAFF"))
                .andExpect(jsonPath("$.routing.deliveries[0].to.nickname").value("운영진"))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons[0]").value("1시간 미답변 · 운영진 우선"))
                .andExpect(jsonPath("$.routing.mine").isEmpty());
    }

    @Test
    void 운영진_단계는_하루_3건에_세지_않는다() {
        TestMember staff = fixtures.staff();
        tag(staff.id(), "인턴");
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    INSERT INTO routing (question_id, member_id, stage, state, sent_at) VALUES (?, ?, 'STAFF', 'SENT', ?)
                    """, question(hours(100)), staff.id(), utc(DAY.minus(Duration.ofHours(1))));
        }
        long question = question(hours(3));

        route(DAY);

        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(staff.id());
    }

    @Test
    void 활동_중인_운영진이_없으면_행을_만들지_않고_다음에_다시_본다() {
        long question = question(hours(1));

        route(DAY);
        assertThat(rows(question)).isEmpty();

        fixtures.staff();
        route(DAY.plus(Duration.ofMinutes(10)));
        assertThat(rows(question)).hasSize(1);
    }

    @Test
    void 지목이_진행_중이면_일반_라우팅을_건너뛰고_넘기면_돌아간다() {
        fixtures.staff();
        TestMember target = candidate("인턴");
        TestMember other = candidate("인턴");
        long question = question(hours(3));
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at) VALUES (?, ?, 'DIRECT', 'SENT', ?)
                """, question, target.id(), utc(DAY.minus(Duration.ofHours(3))));

        route(DAY);
        assertThat(rows(question)).hasSize(1);

        jdbc.update("UPDATE routing SET state = 'PASSED' WHERE question_id = ?", question);
        route(DAY.plus(Duration.ofMinutes(10)));

        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(other.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE question_id = ? AND stage = 'STAFF'",
                Integer.class, question)).isEqualTo(1);
    }

    @Test
    void 밤에_지목한_것은_08시에_보낸다() {
        TestMember target = fixtures.member();
        long question = question(NIGHT);
        assertThat(direct(question, target.id(), NIGHT)).isEqualTo(DirectDelivery.SCHEDULED);
        assertThat(routedNotifications(target.id(), question)).isZero();

        route(MORNING);

        Map<String, Object> row = rows(question).getFirst();
        assertThat(row.get("state")).isEqualTo("SENT");
        assertThat(row.get("sent_at")).isEqualTo(LocalDateTime.ofInstant(MORNING, ZoneOffset.UTC));
        assertThat(routedNotifications(target.id(), question)).isEqualTo(1);
    }

    @Test
    void 밤사이_받을_수_없게_되면_취소하고_일반_라우팅으로_돌아간다() {
        TestMember target = candidate("인턴");
        TestMember other = candidate("인턴");
        long question = question(MORNING.minus(Duration.ofHours(10)));
        direct(question, target.id(), NIGHT);
        jdbc.update("UPDATE member SET suspended_at = UTC_TIMESTAMP(6), suspended_reason = '테스트' WHERE id = ?",
                target.id());

        route(MORNING);

        assertThat(jdbc.queryForObject("SELECT state FROM routing WHERE question_id = ? AND stage = 'DIRECT'",
                String.class, question)).isEqualTo("CANCELED");
        assertThat(routedNotifications(target.id(), question)).isZero();
        assertThat(memberRows(question)).extracting(row -> row.get("member_id")).containsExactly(other.id());
    }

    @Test
    void 밤사이_질문을_지우면_지목을_취소한다() {
        TestMember target = fixtures.member();
        long question = question(NIGHT);
        direct(question, target.id(), NIGHT);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", question);

        route(MORNING);

        assertThat(rows(question).getFirst().get("state")).isEqualTo("CANCELED");
    }

    @Test
    void 받은_사람이_답변하면_ANSWERED이고_답글이나_다른_사람은_바꾸지_않는다() {
        TestMember receiver = fixtures.member();
        TestMember other = fixtures.member();
        long question = question(hours(100));
        sent(question, receiver.id(), DAY);

        answerService.create(other.id(), question, new AnswerCreateRequest("다른 사람의 답변", null, false));
        assertThat(rows(question).getFirst().get("state")).isEqualTo("SENT");

        answerService.create(receiver.id(), question, new AnswerCreateRequest("제 경험으로는요", null, false));

        Map<String, Object> row = rows(question).getFirst();
        assertThat(row.get("state")).isEqualTo("ANSWERED");
        assertThat(row.get("responded_at")).isNotNull();
    }

    @Test
    void 익명_질문의_답변함은_본인에게만_보인다() throws Exception {
        TestMember receiver = fixtures.member();
        long question = fixtures.question(author.id(), true);
        sent(question, receiver.id(), DAY);
        answerService.create(receiver.id(), question, new AnswerCreateRequest("익명으로 답합니다", null, true));

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(author)))
                .andExpect(jsonPath("$.routing.reasonsHidden").value(true))
                .andExpect(jsonPath("$.routing.deliveries[0].state").value("SENT"))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons").isEmpty());
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(receiver)))
                .andExpect(jsonPath("$.routing.deliveries[0].state").value("ANSWERED"))
                .andExpect(jsonPath("$.routing.mine.state").value("ANSWERED"))
                .andExpect(jsonPath("$.routing.mine.canPass").value(false));
    }

    @Test
    void 익명_질문은_질문자_규칙을_쓰지_않는다() {
        jdbc.update("UPDATE member SET department = '컴퓨터공학과', generation = 5 WHERE id = ?", author.id());
        TestMember sameDepartment = candidate("인턴");
        jdbc.update("UPDATE member SET department = '컴퓨터공학과', generation = 1 WHERE id = ?", sameDepartment.id());
        long question = fixtures.question(author.id(), true);
        age(question, hours(3));

        route(DAY);

        var detail = json.readTree((String) memberRows(question).getFirst().get("score_detail"));
        assertThat(detail.path("rule").asDouble()).isEqualTo(0.6);
        assertThat(detail.path("reasons").toString()).doesNotContain("학과", "포지션");
    }

    @Test
    void 실명_질문은_전달_현황에_사유를_보여_주고_받은_사람은_넘길_수_있다() throws Exception {
        jdbc.update("UPDATE member SET department = '컴퓨터공학과' WHERE id = ?", author.id());
        TestMember target = candidate("인턴");
        jdbc.update("UPDATE member SET department = '컴퓨터공학과' WHERE id = ?", target.id());
        long question = question(hours(3));

        route(DAY);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.reasonsHidden").value(false))
                .andExpect(jsonPath("$.routing.deliveries[0].stage").value("MEMBER"))
                .andExpect(jsonPath("$.routing.deliveries[0].state").value("SENT"))
                .andExpect(jsonPath("$.routing.deliveries[0].to.nickname").value(target.nickname()))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons[0]").value("경험: 인턴"))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons[1]").value("같은 학과"))
                .andExpect(jsonPath("$.routing.mine.state").value("SENT"))
                .andExpect(jsonPath("$.routing.mine.canPass").value(true));
    }

    @Test
    void 아무것도_보내지_않았으면_routing은_null이다() throws Exception {
        long question = question(hours(1).minusMinutes(1));

        route(DAY);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(author)))
                .andExpect(jsonPath("$.routing").isEmpty());
    }

    @Test
    void 낮에_지목하면_바로_보내고_알림을_남긴다() {
        TestMember target = fixtures.member();
        long question = fixtures.question(author.id(), false);

        assertThat(direct(question, target.id(), DAY)).isEqualTo(DirectDelivery.SENT);

        Map<String, Object> row = rows(question).getFirst();
        assertThat(row.get("stage")).isEqualTo("DIRECT");
        assertThat(row.get("state")).isEqualTo("SENT");
        assertThat(routedNotifications(target.id(), question)).isEqualTo(1);
    }

    @Test
    void 받을_수_없는_선배를_지목하면_FALLBACK이다() {
        long question = fixtures.question(author.id(), false);
        TestMember off = fixtures.member();
        jdbc.update("UPDATE member SET accept_question = FALSE WHERE id = ?", off.id());
        TestMember hidden = fixtures.member();
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());

        assertThat(direct(question, off.id(), DAY)).isEqualTo(DirectDelivery.FALLBACK);
        assertThat(direct(question, hidden.id(), DAY)).isEqualTo(DirectDelivery.FALLBACK);
        assertThat(direct(question, fixtures.suspended().id(), DAY)).isEqualTo(DirectDelivery.FALLBACK);
        assertThat(direct(question, fixtures.withdrawn().id(), DAY)).isEqualTo(DirectDelivery.FALLBACK);
        assertThat(direct(question, 999_999L, DAY)).isEqualTo(DirectDelivery.FALLBACK);
        assertThat(rows(question)).isEmpty();
    }

    @Test
    void 오늘_3건을_받은_선배를_지목하면_FALLBACK이다() {
        TestMember target = fixtures.member();
        for (int i = 0; i < 3; i++) {
            sent(fixtures.question(author.id(), false), target.id(), DAY.minus(Duration.ofHours(1)));
        }

        assertThat(direct(fixtures.question(author.id(), false), target.id(), DAY)).isEqualTo(DirectDelivery.FALLBACK);
    }

    @Test
    void 질문_작성에서_지목_결과를_돌려주고_쓸_수_없으면_일반_질문으로_저장한다() {
        var response = questionService.create(author.id(), new QuestionCreateRequest(QuestionCategory.JOB, "지목 질문",
                null, false, fixtures.withdrawn().id()));

        assertThat(response.directDelivery()).isEqualTo(DirectDelivery.FALLBACK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE id = ?", Integer.class,
                response.question().id())).isEqualTo(1);
    }

    @Test
    void 본인을_지목하면_400이고_질문은_저장되지_않는다() {
        assertThatThrownBy(() -> questionService.create(author.id(),
                new QuestionCreateRequest(QuestionCategory.JOB, "자기 지목", null, false, author.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE title = '자기 지목'", Integer.class)).isZero();
    }

    @Test
    void 삼일_넘은_질문을_09시에_요약하고_없으면_보내지_않는다() {
        OpsDigestJob digest = new OpsDigestJob(batch, reader, discord, new MutableClock(DAY));
        question(hours(71));

        digest.run();
        assertThat(discordGateway.after(Duration.ofMillis(200), message -> message.channel() == DiscordChannel.STAFF))
                .isEmpty();

        long stale = question(hours(72));
        String title = jdbc.queryForObject("SELECT title FROM question WHERE id = ?", String.class, stale);
        digest.run();

        var sent = discordGateway.await(message -> message.is(DiscordChannel.STAFF, "3일 넘게 답이 없는 질문 1건"));
        assertThat(sent.message().description()).contains(title.split(" ")[0]);
    }

    private void route(Instant now) {
        MutableClock clock = new MutableClock(now);
        RoutingCandidateService candidates = new RoutingCandidateService(config, reader, embeddingService, embeddings,
                members, clock);
        RoutingDelivery delivery = new RoutingDelivery(candidates, config, reader, writer, questions, members,
                notifications, discord, aiLog, embeddingService, transaction, "https://gdghost.test", clock);
        new RoutingJob(batch, delivery, reader, clock).run();
    }

    private DirectDelivery direct(long questionId, long target, Instant now) {
        QuestionRoutingAdapter adapter = new QuestionRoutingAdapter(members, reader, writer, notifications, json,
                new MutableClock(now));
        return transaction.execute(status -> {
            Question question = questions.findById(questionId).orElseThrow();
            return adapter.direct(question, target);
        });
    }

    private static Duration hours(long hours) {
        return Duration.ofHours(hours);
    }

    /** DAY 기준으로 그만큼 지난 질문 */
    private long question(Duration age) {
        return question(DAY.minus(age));
    }

    private long question(Instant createdAt) {
        long question = fixtures.question(author.id(), false);
        jdbc.update("UPDATE question SET created_at = ? WHERE id = ?", utc(createdAt), question);
        return question;
    }

    private void age(long question, Duration age) {
        jdbc.update("UPDATE question SET created_at = ? WHERE id = ?", utc(DAY.minus(age)), question);
    }

    private TestMember candidate(String experience) {
        TestMember member = fixtures.member();
        tag(member.id(), experience);
        return member;
    }

    private void tag(long memberId, String experience) {
        jdbc.update("""
                INSERT INTO member_tag (member_id, tag_id)
                SELECT ?, id FROM tag WHERE kind = 'EXPERIENCE' AND name = ?
                """, memberId, experience);
    }

    private void sent(long questionId, long memberId, Instant at) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at)
                VALUES (?, ?, 'MEMBER', 'SENT', ?)
                """, questionId, memberId, utc(at));
    }

    private static LocalDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
    }

    private List<Map<String, Object>> rows(long questionId) {
        return jdbc.queryForList("SELECT * FROM routing WHERE question_id = ? ORDER BY rank_no, id", questionId);
    }

    private List<Map<String, Object>> memberRows(long questionId) {
        return jdbc.queryForList("""
                SELECT * FROM routing WHERE question_id = ? AND stage = 'MEMBER' ORDER BY rank_no, id
                """, questionId);
    }

    private int routedNotifications(long memberId, long questionId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification
                WHERE member_id = ? AND type = 'ROUTED' AND target_type = 'QUESTION' AND target_id = ?
                """, Integer.class, memberId, questionId);
    }

    private int fallbacks(long questionId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_log WHERE kind = 'EMBED_QUESTION' AND target_id = ? AND state = 'FALLBACK'
                """, Integer.class, questionId);
    }

    private String batchMessage() {
        return jdbc.queryForObject("""
                SELECT message FROM batch_log WHERE job_name = 'routing_10min' ORDER BY id DESC LIMIT 1
                """, String.class);
    }

    private static float[] vector(float value) {
        float[] vector = new float[1024];
        Arrays.fill(vector, value);
        return vector;
    }

}
