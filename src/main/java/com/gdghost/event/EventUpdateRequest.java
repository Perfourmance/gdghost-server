package com.gdghost.event;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다(null은 그대로). 글자 칸은 비울 수 없다 */
public record EventUpdateRequest(
        @Size(min = 2, max = 40) String name,
        LocalDate startDate,
        LocalDate endDate,
        @Size(min = 1, max = 1000) String description,
        @Size(min = 1, max = 100) String requirement,
        @Size(min = 4, max = 4) List<@Pattern(regexp = ".*\\S.*") @Size(max = 60) String> guideSteps) {

    public EventUpdateRequest {
        name = EventCreateRequest.strip(name);
        description = EventCreateRequest.strip(description);
        requirement = EventCreateRequest.strip(requirement);
        guideSteps = EventCreateRequest.strip(guideSteps);
    }

    @AssertTrue(message = "바꿀 필드를 하나 이상 보내야 합니다.")
    boolean isChanging() {
        return name != null || startDate != null || endDate != null || description != null || requirement != null
                || guideSteps != null;
    }

}
