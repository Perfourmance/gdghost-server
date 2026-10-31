package com.gdghost.feed;

import java.time.Instant;
import java.util.List;

import com.gdghost.question.AuthorRef;
import com.gdghost.upload.Attachment;

/** 피드 목록 · 상세 공통 모양. 댓글은 담지 않고 수만 */
public record FeedPostItem(long id, FeedCategory category, AuthorRef author, String body, List<Attachment> photos,
        long likeCount, boolean liked, long commentCount, boolean mine, Instant createdAt) {
}
