package com.gdghost.event;

import java.time.LocalDate;

public record EventSummary(long id, String name, LocalDate startDate, LocalDate endDate, EventState state,
        boolean photoRequired, long participantCount, long approvedCount, My my) {

    public record My(long approvedCount, long pendingCount) {
    }

}
