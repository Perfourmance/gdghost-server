package com.gdghost.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다 */
public record ReportCreateRequest(
        @NotNull ReportTargetType targetType,
        @NotNull Long targetId,
        @NotNull @Size(min = 1, max = 200) String reason) {

    public ReportCreateRequest {
        reason = reason == null ? null : reason.strip();
    }

}
