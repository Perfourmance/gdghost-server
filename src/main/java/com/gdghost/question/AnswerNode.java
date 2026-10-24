package com.gdghost.question;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 답변 · 답글. 지워졌지만 하위 답글이 남은 자리는 author · body 없이 deletedBy만 */
public record AnswerNode(long id, Long parentId, int depth, AuthorRef author, String body, boolean adopted,
        boolean mine, Instant createdAt, Instant editedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String deletedBy, List<AnswerNode> replies) {
}
