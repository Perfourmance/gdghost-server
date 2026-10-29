package com.gdghost.home;

import java.util.List;

/** 부원 홈. 공지(5) · 일정(8) · 공유(9)는 그 단계에서 채우고 그 전엔 빈 배열 · 0 */
public record HomeResponse(List<?> pinnedNotices, int unconfirmedMustReadCount, List<?> recentQuestions,
        List<?> upcomingSchedules, List<?> popularShares) {
}
