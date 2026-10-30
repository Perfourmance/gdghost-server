package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDate;

/** 공지 목록 항목. 본문은 담지 않는다. read = 열어 봄, confirmed = 확인했어요 누름 */
public record NoticeSummary(
        long id,
        String title,
        boolean isPinned,
        boolean isMustRead,
        LocalDate dueDate,
        long commentCount,
        NoticeDetail.Author author,
        boolean read,
        boolean confirmed,
        Instant createdAt) {
}
