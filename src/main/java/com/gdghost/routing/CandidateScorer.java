package com.gdghost.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.gdghost.member.MemberStatus;
import com.gdghost.member.Position;
import com.gdghost.question.QuestionCategory;

/** 후보 점수 계산. DB를 모른다 — 숫자는 MatchingConfig에서만 온다 */
final class CandidateScorer {

    /** 익명 질문이면 questioner는 null */
    record QuestionInput(QuestionCategory category, float[] vector, Questioner questioner) {
    }

    record Questioner(String department, Position position, int generation) {
    }

    record PastAnswer(float[] vector, boolean adopted) {
    }

    record Candidate(long memberId, String department, Position position, int generation, MemberStatus status,
            List<String> experiences, float[] vector, List<PastAnswer> pastAnswers, int answerCount, int passCount,
            Instant lastSeenAt, String matchedInterest) {

        Candidate(long memberId, String department, Position position, int generation, MemberStatus status,
                List<String> experiences, float[] vector, List<PastAnswer> pastAnswers, int answerCount,
                int passCount, Instant lastSeenAt) {
            this(memberId, department, position, generation, status, experiences, vector, pastAnswers, answerCount,
                    passCount, lastSeenAt, null);
        }
    }

    record Scored(long memberId, double rule, double profile, double history, double activity, double relevance,
            double total, List<String> reasons) {
    }

    private final MatchingConfig config;

    CandidateScorer(MatchingConfig config) {
        this.config = config;
    }

    /** activity는 순위에만 쓴다 — 활동이 많다는 이유만으로 무관한 질문의 후보가 되지 않게 */
    List<Scored> rank(QuestionInput question, List<Candidate> candidates, Instant now) {
        return candidates.stream()
                .map(candidate -> score(question, candidate, now))
                .filter(scored -> scored.relevance() >= config.minScore())
                .sorted(Comparator.comparingDouble(Scored::total).reversed().thenComparing(Scored::memberId))
                .limit(config.maxCandidates())
                .toList();
    }

    Scored score(QuestionInput question, Candidate candidate, Instant now) {
        List<String> reasons = new ArrayList<>();
        double rule = rule(question, candidate, reasons);
        double profile = question.vector() == null || candidate.vector() == null ? 0
                : Math.max(0, cosine(question.vector(), candidate.vector()));
        double history = history(question, candidate);
        if (history >= config.history().reasonMin()) {
            reasons.add("비슷한 질문에 답한 적 있음");
        }
        if (reasons.isEmpty() && profile > 0) {
            reasons.add("프로필이 질문과 비슷함");
        }
        double activity = activity(candidate, now);
        double relevance = config.weights().rule() * rule + config.weights().profile() * profile
                + config.weights().history() * history;
        return new Scored(candidate.memberId(), round(rule), round(profile), round(history), round(activity),
                round(relevance), round(relevance + activity), List.copyOf(reasons));
    }

    private double rule(QuestionInput question, Candidate candidate, List<String> reasons) {
        double rule = 0;
        List<String> wanted = config.categoryExperience().getOrDefault(question.category(), List.of());
        List<String> matched = candidate.experiences().stream().filter(wanted::contains).toList();
        if (!matched.isEmpty()) {
            rule += config.rule().experience();
            reasons.add("경험: " + matched.getFirst());
        }
        // 선배 찾기에서 고른 분야
        if (candidate.matchedInterest() != null) {
            rule += config.rule().interest();
            reasons.add(candidate.matchedInterest());
        }
        Questioner questioner = question.questioner();
        if (questioner == null) {
            return rule;
        }
        if (questioner.department() != null && Objects.equals(questioner.department(), candidate.department())) {
            rule += config.rule().department();
            reasons.add("같은 학과");
        }
        if (questioner.position() == candidate.position()) {
            rule += config.rule().position();
            reasons.add("같은 포지션");
        }
        // 기수는 먼저 들어온 쪽이 작다
        if (candidate.status() == MemberStatus.GRADUATED || candidate.generation() < questioner.generation()) {
            rule += config.rule().senior();
        }
        return Math.min(1, rule);
    }

    private double history(QuestionInput question, Candidate candidate) {
        if (question.vector() == null) {
            return 0;
        }
        double best = 0;
        for (PastAnswer past : candidate.pastAnswers()) {
            double similarity = cosine(question.vector(), past.vector())
                    + (past.adopted() ? config.history().adoptedBonus() : 0);
            best = Math.max(best, similarity);
        }
        return Math.min(1, best);
    }

    private double activity(Candidate candidate, Instant now) {
        MatchingConfig.Activity activity = config.activity();
        double score = Math.min(candidate.answerCount(), activity.answerCountCap()) * activity.perAnswer()
                - candidate.passCount() * activity.passPenalty();
        boolean recent = candidate.lastSeenAt() != null
                && candidate.lastSeenAt().isAfter(now.minus(Duration.ofDays(activity.recentDays())));
        return recent ? score + activity.recentBonus() : score;
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

}
