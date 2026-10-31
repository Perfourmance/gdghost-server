package com.gdghost.share;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다. 태그는 중복을 뺀 뒤 개수를 센다 */
public record ShareCreateRequest(
        @NotNull ShareCategory category,
        @NotNull @Size(min = 2, max = 60) String title,
        @NotNull @Size(min = 10, max = 3000) String body,
        @Size(max = 5) List<@NotNull @Size(min = 1, max = 15) String> tags,
        @Size(max = 3) List<@NotNull String> photoKeys) {

    public ShareCreateRequest {
        title = title == null ? null : title.strip();
        body = body == null ? null : body.strip();
        tags = ShareTags.normalize(tags);
    }

}
