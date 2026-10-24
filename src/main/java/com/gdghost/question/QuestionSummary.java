package com.gdghost.question;

import java.time.Instant;
import java.util.List;

/** 부원용 목록 한 줄. 본문 · 답변 내용은 담지 않는다 */
public record QuestionSummary(long id, QuestionCategory category, String title, AuthorRef author,
        QuestionState state, long answerCount, List<AuthorRef> answererPreview, int viewCount, Instant createdAt) {
}
