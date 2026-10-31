package com.gdghost.routing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.gdghost.embedding.EmbeddingService;
import com.gdghost.embedding.EmbeddingStore;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.question.Question;

/**
 * 후보 1–3명 계산. 보내기 직전에 부른다 — 알림 설정 · 하루 3건 · 연속 지목 · 재시도로 채워진 벡터가 그 시점 값으로 반영된다.
 * AI를 부르지 않고 저장된 벡터만 쓴다.
 */
@Service
public class RoutingCandidateService {

    /** @param ruleOnly 질문 벡터가 없어 규칙 점수로 대체했다 */
    record Ranked(List<CandidateScorer.Scored> candidates, boolean ruleOnly) {
    }

    private final MatchingConfig config;
    private final CandidateScorer scorer;
    private final RoutingReader reader;
    private final EmbeddingService embeddings;
    private final EmbeddingStore embeddingStore;
    private final MemberRepository members;
    private final Clock clock;

    public RoutingCandidateService(MatchingConfig config, RoutingReader reader, EmbeddingService embeddings,
            EmbeddingStore embeddingStore, MemberRepository members, Clock clock) {
        this.config = config;
        this.scorer = new CandidateScorer(config);
        this.reader = reader;
        this.embeddings = embeddings;
        this.embeddingStore = embeddingStore;
        this.members = members;
        this.clock = clock;
    }

    Ranked rank(Question question) {
        String model = embeddings.modelId();
        Optional<float[]> vector = embeddingStore.questionVector(question.getId(), model);
        Instant now = clock.instant();
        CandidateScorer.QuestionInput input = new CandidateScorer.QuestionInput(question.getCategory(),
                vector.orElse(null), questioner(question));
        return new Ranked(scorer.rank(input, candidates(question, model, now), now),
                vector.isEmpty() && embeddings.enabled());
    }

    /** 익명 질문은 질문자 정보를 점수에 쓰지 않는다 — 사유나 CSV로 질문자가 특정되지 않게 */
    private CandidateScorer.Questioner questioner(Question question) {
        if (question.isAnonymous() && !config.useQuestionerRulesOnAnonymous()) {
            return null;
        }
        return members.findById(question.getAuthorId())
                .map(RoutingCandidateService::questioner)
                .orElse(null);
    }

    private static CandidateScorer.Questioner questioner(Member author) {
        return new CandidateScorer.Questioner(author.getDepartment(), author.getPosition(), author.getGeneration());
    }

    private List<CandidateScorer.Candidate> candidates(Question question, String model, Instant now) {
        Set<Long> routed = reader.alreadyRouted(question.getId());
        Set<Long> previous = reader.previousQuestionRouted(question.getId());
        List<RoutingReader.PoolMember> pool = reader.pool(question.getAuthorId()).stream()
                .filter(member -> !routed.contains(member.id()) && !previous.contains(member.id()))
                .toList();
        List<Long> ids = pool.stream().map(RoutingReader.PoolMember::id).toList();
        Map<Long, Integer> today = reader.receivedToday(ids, now);
        Map<Long, List<String>> experiences = reader.experiences(ids);
        Map<Long, float[]> vectors = embeddingStore.activeMemberVectors(model);
        Map<Long, List<CandidateScorer.PastAnswer>> past = reader.pastAnswers(ids, question.getId(), model);
        Map<Long, Integer> answers = reader.answerCounts(ids);
        Map<Long, Integer> passes = reader.passCounts(ids);
        return pool.stream()
                .filter(member -> today.getOrDefault(member.id(), 0) < RoutingReader.DAILY_LIMIT)
                .map(member -> new CandidateScorer.Candidate(member.id(), member.department(), member.position(),
                        member.generation(), member.status(), experiences.getOrDefault(member.id(), List.of()),
                        vectors.get(member.id()), past.getOrDefault(member.id(), List.of()),
                        answers.getOrDefault(member.id(), 0), passes.getOrDefault(member.id(), 0),
                        member.lastSeenAt()))
                .toList();
    }

}
