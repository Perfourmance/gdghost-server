package com.gdghost.feed;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다. parentId가 없으면 댓글, 있으면 답글 */
public record FeedCommentCreateRequest(@NotNull @Size(min = 1, max = 300) String body, Long parentId) {

    public FeedCommentCreateRequest {
        body = body == null ? null : body.strip();
    }

}
