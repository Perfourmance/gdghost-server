package com.gdghost.schedule;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다(null은 그대로). 장소 · 메모는 빈 문자열이면 지우고, 종료일은 clearEndDate로 지운다 */
public record ScheduleUpdateRequest(
        ScheduleKind kind,
        @Size(min = 2, max = 80) String title,
        LocalDate startDate,
        LocalDate endDate,
        Boolean clearEndDate,
        @Size(max = 100) String place,
        @Size(max = 1000) String memo) {

    public ScheduleUpdateRequest {
        title = title == null ? null : title.strip();
        place = place == null ? null : place.strip();
        memo = memo == null ? null : memo.strip();
    }

    static String textOrNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    boolean clearingEndDate() {
        return Boolean.TRUE.equals(clearEndDate);
    }

    @AssertTrue(message = "바꿀 필드를 하나 이상 보내야 합니다.")
    boolean isChanging() {
        return kind != null || title != null || startDate != null || endDate != null || clearingEndDate()
                || place != null || memo != null;
    }

    @AssertTrue(message = "종료일과 종료일 지우기를 함께 보낼 수 없습니다.")
    boolean isEndDateClear() {
        return endDate == null || !clearingEndDate();
    }

}
