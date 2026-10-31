package com.gdghost.event;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CertificationReviewRequest(
        @NotNull Decision decision,
        @Size(min = 2, max = 100) String rejectReason) {

    public enum Decision {
        APPROVE, REJECT
    }

    public CertificationReviewRequest {
        rejectReason = EventCreateRequest.strip(rejectReason);
    }

    @AssertTrue(message = "반려는 사유가 필요합니다.")
    boolean isReasonGiven() {
        return decision != Decision.REJECT || rejectReason != null;
    }

}
