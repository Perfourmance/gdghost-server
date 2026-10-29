package com.gdghost.question;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다(null은 그대로). 앞뒤 공백은 떼고 검사, 빈 본문("")은 본문을 지운다. 익명 여부는 보내면 400 */
public record QuestionUpdateRequest(
        QuestionCategory category,
        @Size(min = 2, max = 80) String title,
        @Size(max = 3000) String body,
        @Null(message = "익명 여부는 바꿀 수 없습니다.") Boolean anonymous) {

    public QuestionUpdateRequest {
        title = title == null ? null : title.strip();
        body = body == null ? null : body.strip();
    }

    @AssertTrue(message = "바꿀 필드를 하나 이상 보내야 합니다.")
    boolean isChanging() {
        return category != null || title != null || body != null;
    }

}
