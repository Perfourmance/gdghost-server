package com.gdghost.home;

import java.util.List;

import com.gdghost.notice.PinnedNotice;

/** 부원 홈 */
public record HomeResponse(List<PinnedNotice> pinnedNotices, long unconfirmedMustReadCount, List<?> recentQuestions,
        List<?> upcomingSchedules, List<?> popularShares) {
}
