package com.gdghost.share;

import java.time.Instant;
import java.util.List;

import com.gdghost.question.AuthorRef;

/** 목록 한 줄. 본문 · 사진 주소는 담지 않는다 */
public record ShareSummary(long id, ShareCategory category, String title, List<String> tags, AuthorRef author,
        long saveCount, long noteCount, boolean saved, boolean hasPhoto, Instant createdAt) {
}
