package com.gdghost.event;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventCreateRequest(
        @NotBlank @Size(min = 2, max = 40) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 100) String requirement,
        @NotNull @Size(min = 4, max = 4) List<@NotBlank @Size(max = 60) String> guideSteps) {

    public EventCreateRequest {
        name = strip(name);
        description = strip(description);
        requirement = strip(requirement);
        guideSteps = strip(guideSteps);
    }

    static String strip(String text) {
        return text == null ? null : text.strip();
    }

    static List<String> strip(List<String> steps) {
        return steps == null ? null : steps.stream().map(EventCreateRequest::strip).toList();
    }

    @AssertTrue(message = "종료일은 시작일 이후여야 합니다.")
    boolean isPeriodValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

}
