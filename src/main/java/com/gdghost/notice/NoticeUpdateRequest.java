package com.gdghost.notice;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다(null은 그대로). 마감일을 지워 상시로 돌리려면 clearDueDate */
public record NoticeUpdateRequest(
        @Size(min = 2, max = 80) String title,
        @Size(min = 1, max = 5000) String body,
        Boolean isPinned,
        Boolean isMustRead,
        LocalDate dueDate,
        Boolean clearDueDate) {

    public NoticeUpdateRequest {
        title = title == null ? null : title.strip();
        body = body == null ? null : body.strip();
    }

    boolean clearingDueDate() {
        return Boolean.TRUE.equals(clearDueDate);
    }

    @AssertTrue(message = "바꿀 필드를 하나 이상 보내야 합니다.")
    boolean isChanging() {
        return title != null || body != null || isPinned != null || isMustRead != null || dueDate != null
                || clearingDueDate();
    }

    @AssertTrue(message = "마감일과 마감일 지우기를 함께 보낼 수 없습니다.")
    boolean isDueDateClear() {
        return dueDate == null || !clearingDueDate();
    }

}
