package com.gdghost.recruit;

import java.time.Instant;
import java.util.List;

public record RecruitDetail(long id, RecruitKind kind, String title, Instant meetAt, int capacity, int joinedCount,
        RecruitState state, boolean joined, boolean mine, RecruitPerson author, String memo,
        List<RecruitPerson> participants) {

    static RecruitDetail of(RecruitSummary summary, List<RecruitPerson> participants) {
        return new RecruitDetail(summary.id(), summary.kind(), summary.title(), summary.meetAt(), summary.capacity(),
                summary.joinedCount(), summary.state(), summary.joined(), summary.mine(), summary.author(),
                summary.memo(), participants);
    }

}
