package com.gdghost.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.embedding.EmbeddingStore;
import com.gdghost.member.MemberRepository;
import com.gdghost.notification.NotificationWriter;
import com.gdghost.question.DirectDelivery;
import com.gdghost.question.Question;
import com.gdghost.question.QuestionCategory;
import com.gdghost.question.QuestionCreateRequest;
import com.gdghost.question.QuestionRepository;
import com.gdghost.question.QuestionService;
import com.gdghost.support.FakeEmbeddingClient;
import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;
import com.gdghost.support.MutableClock;

import org.assertj.core.api.ThrowableAssert;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class RoutingIntegrationTest {

    private static final String MODEL = "@cf/baai/bge-m3";
    private static final Instant DAY = Instant.parse("2026-10-31T03:00:00Z"); // KST 12시
    private static final Instant NIGHT = Instant.parse("2026-10-31T14:00:00Z"); // KST 23시

    @Autowired
    MockMvc mvc;

    @Autowired
    Fixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FakeEmbeddingClient client;

    @Autowired
    EmbeddingStore embeddings;

    @Autowired
    RoutingCandidateService candidates;

    @Autowired
    QuestionService questionService;

    @Autowired
    MemberRepository members;

    @Autowired
    QuestionRepository questions;

    @Autowired
    RoutingReader reader;

    @Autowired
    RoutingWriter writer;

    @Autowired
    NotificationWriter notifications;

    @Autowired
    ObjectMapper json;

    @Autowired
    TransactionTemplate transaction;

    private TestMember author;

    @BeforeEach
    void setUp() {
        client.clear();
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
    void 후보를_순위와_점수_내역으로_저장한다() {
        TestMember tagged = candidate("인턴");
        embeddings.saveMember(tagged.id(), vector(0.5f), MODEL, "hash");
        TestMember similar = fixtures.member();
        embeddings.saveMember(similar.id(), vector(0.5f), MODEL, "hash");
        fixtures.member(); // 벡터도 태그도 없으면 최소 점수에 못 미친다
        long question = fixtures.question(author.id(), false);
        embeddings.saveQuestion(question, vector(0.5f), MODEL);

        candidates.compute(question);

        List<Map<String, Object>> rows = rows(question);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("member_id")).isEqualTo(tagged.id());
        assertThat(rows.get(0).get("rank_no")).isNotNull();
        assertThat(rows.get(0).get("stage")).isEqualTo("MEMBER");
        assertThat(rows.get(0).get("state")).isEqualTo("CANDIDATE");
        assertThat(rows.get(0).get("sent_at")).isNull();
        assertThat(rows.get(1).get("member_id")).isEqualTo(similar.id());
        var detail = json.readTree((String) rows.get(0).get("score_detail"));
        assertThat(detail.path("version").asString()).isEqualTo("2026-10-v1");
        assertThat(detail.path("rule").asDouble()).isGreaterThanOrEqualTo(0.6);
        assertThat(detail.path("reasons").get(0).asString()).isEqualTo("경험: 인턴");
    }

    @Test
    void 받을_수_없는_사람은_후보에서_뺀다() {
        TestMember off = candidate("인턴");
        jdbc.update("UPDATE member SET accept_question = FALSE WHERE id = ?", off.id());
        TestMember hidden = candidate("인턴");
        jdbc.update("UPDATE member SET hidden_from_list = TRUE WHERE id = ?", hidden.id());
        TestMember suspended = fixtures.suspended();
        tag(suspended.id(), "인턴");
        TestMember full = candidate("인턴");
        for (int i = 0; i < 3; i++) {
            sent(fixtures.question(author.id(), false), full.id());
        }
        tag(author.id(), "인턴");
        TestMember ok = candidate("인턴");
        long question = fixtures.question(author.id(), false);

        candidates.compute(question);

        assertThat(rows(question)).extracting(row -> row.get("member_id")).containsExactly(ok.id());
    }

    @Test
    void 직전_질문에서_알림을_받은_사람만_연달아_후보가_되지_않는다() {
        TestMember received = candidate("인턴");
        TestMember notified = candidate("인턴");
        TestMember onlyCandidate = candidate("인턴");
        long previous = fixtures.question(author.id(), false);
        sent(previous, received.id());
        jdbc.update("INSERT INTO routing (question_id, member_id, stage, state) VALUES (?, ?, 'MEMBER', 'CANDIDATE')",
                previous, onlyCandidate.id());
        long question = fixtures.question(author.id(), false);

        candidates.compute(question);

        assertThat(rows(question)).extracting(row -> row.get("member_id"))
                .containsExactlyInAnyOrder(notified.id(), onlyCandidate.id());
    }

    @Test
    void 보내지_않은_질문이_끼어_있어도_마지막으로_알림이_나간_질문을_본다() {
        TestMember received = candidate("인턴");
        TestMember other = candidate("인턴");
        sent(fixtures.question(author.id(), false), received.id());
        candidates.compute(fixtures.question(author.id(), false));
        long question = fixtures.question(author.id(), false);

        candidates.compute(question);

        assertThat(rows(question)).extracting(row -> row.get("member_id")).containsExactly(other.id());
    }

    @Test
    void 임베딩이_없으면_규칙_점수로_대체하고_FALLBACK을_남긴다() {
        TestMember tagged = candidate("인턴");
        TestMember similar = fixtures.member();
        embeddings.saveMember(similar.id(), vector(0.5f), MODEL, "hash");
        long question = fixtures.question(author.id(), false);

        candidates.compute(question);

        assertThat(rows(question)).extracting(row -> row.get("member_id")).containsExactly(tagged.id());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_log WHERE kind = 'EMBED_QUESTION' AND target_id = ? AND state = 'FALLBACK'",
                Integer.class, question)).isEqualTo(1);
    }

    @Test
    void 익명_질문은_질문자_규칙을_쓰지_않고_사유를_숨긴다() throws Exception {
        jdbc.update("UPDATE member SET department = '컴퓨터공학과', generation = 5 WHERE id = ?", author.id());
        TestMember sameDepartment = candidate("인턴");
        jdbc.update("UPDATE member SET department = '컴퓨터공학과', generation = 1 WHERE id = ?", sameDepartment.id());
        long question = fixtures.question(author.id(), true);

        candidates.compute(question);

        var detail = json.readTree((String) rows(question).getFirst().get("score_detail"));
        assertThat(detail.path("rule").asDouble()).isEqualTo(0.6);
        assertThat(detail.path("reasons").toString()).doesNotContain("학과", "포지션");
        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.reasonsHidden").value(true))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons").isEmpty())
                .andExpect(jsonPath("$.routing.mine").isEmpty());
    }

    @Test
    void 실명_질문은_질문자_규칙을_쓰고_전달_현황에_사유를_보여_준다() throws Exception {
        jdbc.update("UPDATE member SET department = '컴퓨터공학과' WHERE id = ?", author.id());
        TestMember target = candidate("인턴");
        jdbc.update("UPDATE member SET department = '컴퓨터공학과' WHERE id = ?", target.id());
        long question = fixtures.question(author.id(), false);

        candidates.compute(question);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.reasonsHidden").value(false))
                .andExpect(jsonPath("$.routing.deliveries[0].stage").value("MEMBER"))
                .andExpect(jsonPath("$.routing.deliveries[0].state").value("CANDIDATE"))
                .andExpect(jsonPath("$.routing.deliveries[0].to.nickname").value(target.nickname()))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons[0]").value("경험: 인턴"))
                .andExpect(jsonPath("$.routing.deliveries[0].reasons[1]").value("같은 학과"))
                .andExpect(jsonPath("$.routing.mine.state").value("CANDIDATE"))
                .andExpect(jsonPath("$.routing.mine.canPass").value(false));
    }

    @Test
    void 후보가_없으면_routing은_null이다() throws Exception {
        long question = fixtures.question(author.id(), false);

        candidates.compute(question);

        mvc.perform(get("/api/v1/questions/{id}", question).cookie(fixtures.loginCookie(author)))
                .andExpect(jsonPath("$.routing").isEmpty());
    }

    @Test
    void 다시_계산하면_보내지_않은_후보만_바꾸고_받은_사람은_다시_넣지_않는다() {
        TestMember received = candidate("인턴");
        TestMember stale = candidate("인턴");
        long question = fixtures.question(author.id(), false);
        sent(question, received.id());
        jdbc.update("INSERT INTO routing (question_id, member_id, stage, state) VALUES (?, ?, 'MEMBER', 'CANDIDATE')",
                question, stale.id());
        jdbc.update("DELETE FROM member_tag WHERE member_id = ?", stale.id());
        TestMember fresh = candidate("인턴");

        candidates.compute(question);

        assertThat(rows(question)).extracting(row -> row.get("member_id"))
                .containsExactlyInAnyOrder(received.id(), fresh.id());
        assertThat(jdbc.queryForObject("SELECT state FROM routing WHERE question_id = ? AND member_id = ?",
                String.class, question, received.id())).isEqualTo("SENT");
    }

    @Test
    void 지운_질문은_후보를_만들지_않는다() {
        candidate("인턴");
        long question = fixtures.question(author.id(), false);
        jdbc.update("UPDATE question SET deleted_at = UTC_TIMESTAMP(6), deleted_by = 'AUTHOR' WHERE id = ?", question);

        candidates.compute(question);

        assertThat(rows(question)).isEmpty();
    }

    @Test
    void 낮에_지목하면_바로_보내고_알림을_남긴다() {
        TestMember target = fixtures.member();
        long question = fixtures.question(author.id(), false);

        assertThat(direct(question, target.id(), DAY)).isEqualTo(DirectDelivery.SENT);

        Map<String, Object> row = rows(question).getFirst();
        assertThat(row.get("stage")).isEqualTo("DIRECT");
        assertThat(row.get("state")).isEqualTo("SENT");
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification
                WHERE member_id = ? AND type = 'ROUTED' AND target_type = 'QUESTION' AND target_id = ?
                """, Integer.class, target.id(), question)).isEqualTo(1);
    }

    @Test
    void 밤에_지목하면_보내지_않고_08시를_기다린다() {
        TestMember target = fixtures.member();
        long question = fixtures.question(author.id(), false);

        assertThat(direct(question, target.id(), NIGHT)).isEqualTo(DirectDelivery.SCHEDULED);

        Map<String, Object> row = rows(question).getFirst();
        assertThat(row.get("stage")).isEqualTo("DIRECT");
        assertThat(row.get("state")).isEqualTo("CANDIDATE");
        assertThat(row.get("sent_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE member_id = ? AND type = 'ROUTED'",
                Integer.class, target.id())).isZero();
    }

    @Test
    void 받을_수_없는_선배를_지목하면_FALLBACK이고_질문은_남는다() {
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
            long other = fixtures.question(author.id(), false);
            jdbc.update("""
                    INSERT INTO routing (question_id, member_id, stage, state, sent_at)
                    VALUES (?, ?, 'MEMBER', 'SENT', ?)
                    """, other, target.id(), java.time.LocalDateTime.parse("2026-10-31T01:00:00"));
        }

        assertThat(direct(fixtures.question(author.id(), false), target.id(), DAY)).isEqualTo(DirectDelivery.FALLBACK);
    }

    @Test
    void 지목한_선배는_일반_후보에서_빠진다() {
        TestMember target = candidate("인턴");
        TestMember other = candidate("인턴");
        long question = fixtures.question(author.id(), false);
        direct(question, target.id(), DAY);

        candidates.compute(question);

        assertThat(rows(question)).extracting(row -> row.get("member_id"))
                .containsExactlyInAnyOrder(target.id(), other.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routing WHERE question_id = ? AND member_id = ?",
                Integer.class, question, target.id())).isEqualTo(1);
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
        ThrowableAssert.ThrowingCallable create = () -> questionService.create(author.id(),
                new QuestionCreateRequest(QuestionCategory.JOB, "자기 지목", null, false, author.id()));

        assertThatThrownBy(create).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE title = '자기 지목'", Integer.class)).isZero();
    }

    private DirectDelivery direct(long questionId, long target, Instant now) {
        QuestionRoutingAdapter adapter = new QuestionRoutingAdapter(members, reader, writer, notifications, json,
                new MutableClock(now));
        return transaction.execute(status -> {
            Question question = questions.findById(questionId).orElseThrow();
            return adapter.direct(question, target);
        });
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

    private void sent(long questionId, long memberId) {
        jdbc.update("""
                INSERT INTO routing (question_id, member_id, stage, state, sent_at)
                VALUES (?, ?, 'MEMBER', 'SENT', UTC_TIMESTAMP(6))
                """, questionId, memberId);
    }

    private List<Map<String, Object>> rows(long questionId) {
        return jdbc.queryForList("SELECT * FROM routing WHERE question_id = ? ORDER BY rank_no, id", questionId);
    }

    private static float[] vector(float value) {
        float[] vector = new float[1024];
        Arrays.fill(vector, value);
        return vector;
    }

}
