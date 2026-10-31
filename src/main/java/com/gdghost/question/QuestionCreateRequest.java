package com.gdghost.question;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다. 빈 본문은 null */
public record QuestionCreateRequest(
        @NotNull QuestionCategory category,
        @NotNull @Size(min = 2, max = 80) String title,
        @Size(max = 3000) String body,
        Boolean anonymous,
        Long targetMemberId) {

    public QuestionCreateRequest {
        title = title == null ? null : title.strip();
        body = body == null || body.isBlank() ? null : body.strip();
    }

    boolean isAnonymous() {
        return Boolean.TRUE.equals(anonymous);
    }

}
