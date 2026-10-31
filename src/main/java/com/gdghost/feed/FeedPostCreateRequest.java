package com.gdghost.feed;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다 */
public record FeedPostCreateRequest(
        @NotNull FeedCategory category,
        @NotNull @Size(min = 1, max = 1000) String body,
        @Size(max = 3) List<@NotNull String> photoKeys) {

    public FeedPostCreateRequest {
        body = body == null ? null : body.strip();
    }

}
