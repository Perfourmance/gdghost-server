package com.gdghost.recruit;

import java.time.Instant;

public record RecruitSummary(long id, RecruitKind kind, String title, Instant meetAt, int capacity, int joinedCount,
        RecruitState state, boolean joined, boolean mine, RecruitPerson author, String memo) {
}
