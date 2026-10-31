package com.gdghost.recruit;

public record RecruitJoinResponse(boolean joined, int joinedCount, int capacity, RecruitState state) {
}
