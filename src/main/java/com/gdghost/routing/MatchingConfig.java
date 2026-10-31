package com.gdghost.routing;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.gdghost.question.QuestionCategory;

/** 점수 공식의 숫자. AI팀이 고치는 곳 (application.yaml의 matching) */
@ConfigurationProperties("matching")
public record MatchingConfig(String version, Weights weights, Rule rule, History history, Activity activity,
        double minScore, int maxCandidates, boolean useQuestionerRulesOnAnonymous,
        Map<QuestionCategory, List<String>> categoryExperience) {

    public record Weights(double rule, double profile, double history) {
    }

    public record Rule(double experience, double department, double position, double senior) {
    }

    public record History(double adoptedBonus, double reasonMin) {
    }

    public record Activity(int answerCountCap, double perAnswer, int recentDays, double recentBonus,
            double passPenalty) {
    }

}
