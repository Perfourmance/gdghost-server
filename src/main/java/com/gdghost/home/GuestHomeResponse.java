package com.gdghost.home;

import java.util.List;

import com.gdghost.notice.GuestPinnedNotice;

/** 비회원 홈. 부원과 같은 키에서 개인 값(unconfirmedMustReadCount)만 없고 항목은 제목까지만 */
public record GuestHomeResponse(List<GuestPinnedNotice> pinnedNotices, List<?> recentQuestions, List<?> upcomingSchedules,
        List<?> popularShares) {
}
