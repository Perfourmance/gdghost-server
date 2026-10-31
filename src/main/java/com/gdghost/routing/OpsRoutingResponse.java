package com.gdghost.routing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.gdghost.question.AuthorRef;
import com.gdghost.question.QuestionCategory;

public final class OpsRoutingResponse {

    private OpsRoutingResponse() {
    }

    public record StaleQuestion(long id, QuestionCategory category, String title, AuthorRef author, Instant createdAt,
            long waitingDays, int sentCount, int passedCount) {
    }

    public enum LogState {
        SENT, ANSWERED, PASSED, CANCELED
    }

    /** @param nickname 탈퇴자는 null */
    public record LogMember(long id, String nickname) {
    }

    /** @param scoreDetail 지목은 점수 계산이 없어 null */
    public record Log(long id, long questionId, String questionTitle, LogMember member, String stage, Integer rankNo,
            String state, Instant sentAt, Instant respondedAt, Map<String, Object> scoreDetail) {
    }

    public record Stats(long sentCount, long answeredCount, long passedCount, long staffFirstCount,
            double conversionRate, double passRate, double fallbackRate, List<VersionStats> byVersion) {
    }

    public record VersionStats(String version, long sentCount, double conversionRate, double passRate) {
    }

}
