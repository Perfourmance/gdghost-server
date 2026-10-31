package com.gdghost.share;

import java.time.Instant;

import com.gdghost.question.GuestAuthor;

/** 비회원 목록 한 줄 — 제목까지만 */
public record GuestShareSummary(long id, ShareCategory category, String title, GuestAuthor author, Instant createdAt) {
}
