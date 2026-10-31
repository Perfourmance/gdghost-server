package com.gdghost.schedule;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScheduleCreateRequest(
        @NotNull ScheduleKind kind,
        @NotBlank @Size(min = 2, max = 80) String title,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 100) String place,
        @Size(max = 1000) String memo) {

    public ScheduleCreateRequest {
        title = title == null ? null : title.strip();
        place = ScheduleUpdateRequest.textOrNull(place);
        memo = ScheduleUpdateRequest.textOrNull(memo);
    }

    @AssertTrue(message = "종료일은 시작일 이후여야 합니다.")
    boolean isPeriodValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

}
