package com.gdghost.notice;

import java.time.LocalDate;

/** 홈 고정 공지 (부원) */
public record PinnedNotice(long id, String title, boolean isMustRead, LocalDate dueDate, boolean read) {
}
