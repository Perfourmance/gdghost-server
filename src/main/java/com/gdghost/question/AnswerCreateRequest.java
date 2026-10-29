package com.gdghost.question;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다. 답변 5자 이상은 parentId에 따라 달라 서비스에서 본다 */
public record AnswerCreateRequest(
        @NotNull @Size(min = 1, max = 3000) String body,
        Long parentId,
        Boolean anonymous) {

    public AnswerCreateRequest {
        body = body == null ? null : body.strip();
    }

    boolean isAnonymous() {
        return Boolean.TRUE.equals(anonymous);
    }

}
