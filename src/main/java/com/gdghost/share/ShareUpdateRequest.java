package com.gdghost.share;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다(null은 그대로). 태그 · 사진은 보내면 통째로 교체, 빈 배열은 전부 지운다 */
public record ShareUpdateRequest(
        ShareCategory category,
        @Size(min = 2, max = 60) String title,
        @Size(min = 10, max = 3000) String body,
        @Size(max = 5) List<@NotNull @Size(min = 1, max = 15) String> tags,
        @Size(max = 3) List<@NotNull String> photoKeys) {

    public ShareUpdateRequest {
        title = title == null ? null : title.strip();
        body = body == null ? null : body.strip();
        tags = ShareTags.normalize(tags);
    }

    @AssertTrue(message = "바꿀 필드를 하나 이상 보내야 합니다.")
    boolean isChanging() {
        return category != null || title != null || body != null || tags != null || photoKeys != null;
    }

}
