package com.gdghost.home;

import java.util.List;

import com.gdghost.notice.PinnedNotice;

/** 부원 홈. 공유(9)는 그 단계에서 채우고 그 전엔 빈 배열 */
public record HomeResponse(List<PinnedNotice> pinnedNotices, long unconfirmedMustReadCount, List<?> recentQuestions,
        List<?> upcomingSchedules, List<?> popularShares) {
}
