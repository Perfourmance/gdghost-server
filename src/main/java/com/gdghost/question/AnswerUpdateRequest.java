package com.gdghost.question;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 본문만 바꾼다. 앞뒤 공백은 떼고 검사, 답변 5자 이상은 서비스에서 본다 */
public record AnswerUpdateRequest(@NotNull @Size(min = 1, max = 3000) String body) {

    public AnswerUpdateRequest {
        body = body == null ? null : body.strip();
    }

}
