package com.gdghost.feed;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 신고 접수 · 처리에서 피드 글을 다루는 길. lock으로 글 행을 먼저 잠근 뒤 hide · restore를 부른다 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FeedModeration {

    /** deletedBy는 null · AUTHOR · STAFF */
    public record Content(long authorId, String deletedBy) {
    }

    private final FeedPostRepository posts;

    public FeedModeration(FeedPostRepository posts) {
        this.posts = posts;
    }

    public boolean isLive(long postId) {
        return posts.findById(postId).filter(p -> !p.isDeleted()).isPresent();
    }

    public Optional<Content> lock(long postId) {
        return posts.findForUpdate(postId).map(p -> new Content(p.getAuthorId(), p.getDeletedBy()));
    }

    /** 사진은 되돌릴 수 있게 그대로 둔다 — 숨김 글은 주소가 발급되지 않는다 */
    public void hide(long postId, Instant now) {
        posts.findById(postId).orElseThrow().hideByStaff(now);
    }

    public void restore(long postId) {
        posts.findById(postId).orElseThrow().restore();
    }

}
