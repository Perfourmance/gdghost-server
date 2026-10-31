package com.gdghost.question;

import java.time.Instant;
import java.util.List;

/** routing(전달 현황)은 후보도 발송도 없으면 null */
public record QuestionDetail(long id, QuestionCategory category, String title, String body, boolean anonymous,
        AuthorRef author, QuestionState state, Long adoptedAnswerId, boolean editedAfterAnswer, int viewCount,
        Instant createdAt, Instant updatedAt, boolean mine, boolean canAnswer, boolean canPostAnonymously,
        List<AnswerNode> answers, QuestionRouting routing) {
}
