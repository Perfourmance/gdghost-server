package com.gdghost.question;

import java.time.Instant;

/** 비회원 목록 한 줄 — 제목까지만 */
public record GuestQuestionSummary(long id, QuestionCategory category, String title, GuestAuthor author,
        Instant createdAt, long answerCount) {
}
