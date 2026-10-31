package com.gdghost.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gdghost.member.MemberStatus;
import com.gdghost.member.Position;
import com.gdghost.question.QuestionCategory;
import com.gdghost.routing.CandidateScorer.Candidate;
import com.gdghost.routing.CandidateScorer.PastAnswer;
import com.gdghost.routing.CandidateScorer.QuestionInput;
import com.gdghost.routing.CandidateScorer.Questioner;
import com.gdghost.routing.CandidateScorer.Scored;

class CandidateScorerTest {

    private static final Instant NOW = Instant.parse("2026-10-31T03:00:00Z");
    private static final float[] SAME = vector(1, 1);
    private static final float[] OPPOSITE = vector(-1, -1);
    private static final float[] ORTHOGONAL = vector(1, -1);

    private final MatchingConfig config = new MatchingConfig("v1", new MatchingConfig.Weights(0.4, 0.3, 0.3),
            new MatchingConfig.Rule(0.6, 0.2, 0.1, 0.1), new MatchingConfig.History(0.1, 0.6),
            new MatchingConfig.Activity(10, 0.01, 30, 0.05, 0.05), 0.15, 3, false,
            Map.of(QuestionCategory.JOB, List.of("인턴", "해커톤")));
    private final CandidateScorer scorer = new CandidateScorer(config);

    private static float[] vector(float a, float b) {
        float[] vector = new float[4];
        Arrays.fill(vector, 0, 2, a);
        Arrays.fill(vector, 2, 4, b);
        return vector;
    }

    private static Candidate candidate(long id, List<String> experiences, float[] vector) {
        return new Candidate(id, "컴퓨터공학과", Position.DEV, 3, MemberStatus.ENROLLED, experiences, vector, List.of(), 0,
                0, null);
    }

    private static QuestionInput question(float[] vector, Questioner questioner) {
        return new QuestionInput(QuestionCategory.JOB, vector, questioner);
    }

    @Test
    void 항목별_점수와_합계를_설계_공식대로_계산한다() {
        Candidate candidate = new Candidate(1, "컴퓨터공학과", Position.DEV, 3, MemberStatus.ENROLLED, List.of("인턴"), SAME,
                List.of(new PastAnswer(ORTHOGONAL, true)), 20, 1, NOW.minus(Duration.ofDays(1)));

        Scored scored = scorer.score(question(SAME, null), candidate, NOW);

        assertThat(scored.rule()).isEqualTo(0.6);
        assertThat(scored.profile()).isEqualTo(1.0);
        assertThat(scored.history()).isEqualTo(0.1); // 유사도 0 + 채택 가산
        assertThat(scored.activity()).isEqualTo(0.1); // 상한 10 × 0.01 + 최근 0.05 − 넘김 0.05
        assertThat(scored.relevance()).isEqualTo(0.57);
        assertThat(scored.total()).isEqualTo(0.67);
        assertThat(scored.reasons()).containsExactly("경험: 인턴");
    }

    @Test
    void 질문자_규칙은_학과_포지션_선배를_더하고_상한은_1이다() {
        Candidate candidate = candidate(1, List.of("인턴"), SAME);

        Scored scored = scorer.score(question(SAME, new Questioner("컴퓨터공학과", Position.DEV, 5)), candidate, NOW);

        assertThat(scored.rule()).isEqualTo(1.0);
        assertThat(scored.reasons()).containsExactly("경험: 인턴", "같은 학과", "같은 포지션");
    }

    @Test
    void 익명_질문이면_질문자_규칙과_사유가_빠진다() {
        Scored scored = scorer.score(question(SAME, null), candidate(1, List.of(), SAME), NOW);

        assertThat(scored.rule()).isZero();
        assertThat(scored.reasons()).doesNotContain("같은 학과", "같은 포지션");
    }

    @Test
    void 최소_점수는_활동_점수를_빼고_판단한다() {
        Candidate active = new Candidate(1, null, Position.DEV, 3, MemberStatus.ENROLLED, List.of(), ORTHOGONAL,
                List.of(), 100, 0, NOW);

        assertThat(scorer.score(question(SAME, null), active, NOW).total()).isEqualTo(0.15);
        assertThat(scorer.rank(question(SAME, null), List.of(active), NOW)).isEmpty();
    }

    @Test
    void 태그가_안_맞아도_유사도가_0점5면_후보가_되고_더_낮으면_빠진다() {
        float[] base = new float[] {1, 0, 0, 0};
        float[] close = new float[] {1, 1.7f, 0, 0}; // 코사인 약 0.51
        float[] far = new float[] {1, 3, 0, 0};       // 코사인 약 0.32

        assertThat(scorer.rank(question(base, null), List.of(candidate(1, List.of(), close)), NOW)).hasSize(1);
        assertThat(scorer.rank(question(base, null), List.of(candidate(2, List.of(), far)), NOW)).isEmpty();
    }

    @Test
    void 벡터가_없으면_규칙_점수만으로_후보가_된다() {
        List<Scored> ranked = scorer.rank(question(null, null),
                List.of(candidate(1, List.of("인턴"), SAME), candidate(2, List.of(), SAME)), NOW);

        assertThat(ranked).extracting(Scored::memberId).containsExactly(1L);
        assertThat(ranked.getFirst().profile()).isZero();
    }

    @Test
    void 합계_순으로_최대_3명만_고른다() {
        List<Candidate> candidates = List.of(candidate(1, List.of(), ORTHOGONAL), candidate(2, List.of("인턴"), SAME),
                candidate(3, List.of("인턴"), ORTHOGONAL), candidate(4, List.of(), SAME),
                candidate(5, List.of("해커톤"), SAME));

        assertThat(scorer.rank(question(SAME, null), candidates, NOW)).extracting(Scored::memberId)
                .containsExactly(2L, 5L, 4L);
    }

    @Test
    void 답변_이력은_가장_비슷한_것을_쓰고_비슷하면_사유를_단다() {
        Candidate candidate = new Candidate(1, null, Position.DEV, 3, MemberStatus.ENROLLED, List.of(), null,
                List.of(new PastAnswer(OPPOSITE, false), new PastAnswer(SAME, false)), 2, 0, null);

        Scored scored = scorer.score(question(SAME, null), candidate, NOW);

        assertThat(scored.history()).isEqualTo(1.0);
        assertThat(scored.reasons()).containsExactly("비슷한 질문에 답한 적 있음");
    }

}
