package com.gdghost.notice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다 */
public record NoticeCommentCreateRequest(@NotNull @Size(min = 1, max = 500) String body) {

    public NoticeCommentCreateRequest {
        body = body == null ? null : body.strip();
    }

}
