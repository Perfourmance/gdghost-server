package com.gdghost.notice;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다. 상단 고정은 기본 켜짐, 필독은 기본 꺼짐. 마감일이 없으면 상시 */
public record NoticeCreateRequest(
        @NotNull @Size(min = 2, max = 80) String title,
        @NotNull @Size(min = 1, max = 5000) String body,
        Boolean isPinned,
        Boolean isMustRead,
        LocalDate dueDate) {

    public NoticeCreateRequest {
        title = title == null ? null : title.strip();
        body = body == null ? null : body.strip();
    }

    boolean pinned() {
        return !Boolean.FALSE.equals(isPinned);
    }

    boolean mustRead() {
        return Boolean.TRUE.equals(isMustRead);
    }

}
