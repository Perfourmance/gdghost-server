package com.gdghost.feed;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gdghost.question.AuthorRef;

/**
 * 댓글 하나. 지운 댓글에 답글이 남아 있으면 자리만 남겨 author · body는 null, deletedBy로 문구를 고른다.
 * 답글에는 replies가 없다.
 */
public record FeedCommentItem(long id, Long parentId, int depth, AuthorRef author, String body,
        @JsonInclude(JsonInclude.Include.NON_NULL) String deletedBy, boolean mine, Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FeedCommentItem> replies) {
}
