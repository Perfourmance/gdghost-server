package com.gdghost.event;

import java.time.LocalDate;
import java.util.List;

/** pendingCount는 운영진에게만, 아니면 null */
public record EventDetail(long id, String name, String description, String requirement, List<String> guideSteps,
        LocalDate startDate, LocalDate endDate, EventState state, My my, List<Rank> ranking, Long pendingCount) {

    public record My(long approvedCount, long pendingCount, List<Rejected> rejected) {
    }

    public record Rejected(long id, String rejectReason) {
    }

    public record Rank(int rank, RankedMember member, long approvedCount) {
    }

    public record RankedMember(long id, String nickname, int generation) {
    }

}
