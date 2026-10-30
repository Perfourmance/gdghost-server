package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDate;

/** 운영 공지 목록 항목. 본문은 담지 않는다. remindedAt은 미확인 재알림(6단계)부터 채워진다 */
public record OpsNoticeResponse(
        long id,
        String title,
        boolean isPinned,
        boolean isMustRead,
        LocalDate dueDate,
        Person author,
        NoticeStats stats,
        long commentCount,
        Instant remindedAt,
        Instant createdAt) {

    /** 운영 화면의 사람 표시. 탈퇴했으면 nickname이 null */
    public record Person(long id, String nickname) {
    }

}
