package com.gdghost.recruit;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecruitCreateRequest(
        @NotNull RecruitKind kind,
        @NotBlank @Size(min = 2, max = 60) String title,
        @NotNull Instant meetAt,
        @NotNull @Min(2) @Max(50) Integer capacity,
        @Size(max = 500) String memo,
        Long scheduleId) {

    public RecruitCreateRequest {
        title = title == null ? null : title.strip();
        memo = memo == null || memo.isBlank() ? null : memo.strip();
    }

}
