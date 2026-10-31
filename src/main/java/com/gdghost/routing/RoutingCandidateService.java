package com.gdghost.routing;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.embedding.AiLogWriter;
import com.gdghost.embedding.EmbeddingService;
import com.gdghost.embedding.EmbeddingStore;
import com.gdghost.member.Member;
import com.gdghost.member.MemberRepository;
import com.gdghost.question.Question;
import com.gdghost.question.QuestionRepository;

/**
 * 후보 1–3명 계산. 보내기 직전에 부른다 — 알림 설정 · 하루 3건 · 연속 지목 · 재시도로 채워진 벡터가 그 시점 값으로 반영된다.
 * 호출과 발송은 라우팅 배치(#58)
 */
@Service
public class RoutingCandidateService {

    private final MatchingConfig config;
    private final CandidateScorer scorer;
    private final RoutingReader reader;
    private final RoutingWriter writer;
    private final EmbeddingService embeddings;
    private final EmbeddingStore embeddingStore;
    private final AiLogWriter aiLog;
    private final QuestionRepository questions;
    private final MemberRepository members;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RoutingCandidateService(MatchingConfig config, RoutingReader reader, RoutingWriter writer,
            EmbeddingService embeddings, EmbeddingStore embeddingStore, AiLogWriter aiLog,
            QuestionRepository questions, MemberRepository members, TransactionTemplate transaction, Clock clock) {
        this.config = config;
        this.scorer = new CandidateScorer(config);
        this.reader = reader;
        this.writer = writer;
        this.embeddings = embeddings;
        this.embeddingStore = embeddingStore;
        this.aiLog = aiLog;
        this.questions = questions;
        this.members = members;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** 벡터가 없으면 규칙 점수로 대체한다. 그 기록이 ai_log FALLBACK */
    public void compute(long questionId) {
        Question question = questions.findById(questionId).filter(found -> !found.isDeleted()).orElse(null);
        if (question == null) {
            return;
        }
        String model = embeddings.modelId();
        Optional<float[]> vector = embeddingStore.questionVector(questionId, model);
        if (vector.isEmpty() && embeddings.enabled()) {
            aiLog.record(AiLogWriter.Kind.EMBED_QUESTION, questionId, model, null, AiLogWriter.State.FALLBACK);
        }
        Instant now = clock.instant();
        CandidateScorer.QuestionInput input = new CandidateScorer.QuestionInput(question.getCategory(),
                vector.orElse(null), questioner(question));
        List<CandidateScorer.Scored> ranked = scorer.rank(input, candidates(question, model, now), now);
        // 질문 행을 잠가 수정 · 삭제와 엇갈리지 않게 한다
        transaction.executeWithoutResult(status -> {
            boolean live = questions.findForUpdate(questionId).filter(found -> !found.isDeleted()).isPresent();
            writer.deleteMemberCandidates(questionId);
            if (live) {
                writer.insertCandidates(questionId, ranked, config.version());
            }
        });
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
