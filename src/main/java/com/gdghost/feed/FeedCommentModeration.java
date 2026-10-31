package com.gdghost.feed;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 신고 접수 · 처리에서 피드 댓글을 다루는 길. lock으로 행을 먼저 잠근 뒤 hide · restore를 부른다 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FeedCommentModeration {

    /** deletedBy는 null · AUTHOR · STAFF */
    public record Content(long authorId, String deletedBy) {
    }

    private final FeedCommentRepository comments;
    private final FeedPostRepository posts;

    public FeedCommentModeration(FeedCommentRepository comments, FeedPostRepository posts) {
        this.comments = comments;
        this.posts = posts;
    }

    /** 지운 댓글, 글이 지워진 댓글은 없는 것으로 본다 */
    public boolean isLive(long commentId) {
        return comments.findById(commentId)
                .filter(c -> !c.isDeleted())
                .flatMap(c -> posts.findById(c.getPostId()))
                .filter(p -> !p.isDeleted())
                .isPresent();
    }

    public Optional<Content> lock(long commentId) {
        return comments.findForUpdate(commentId).map(c -> new Content(c.getAuthorId(), c.getDeletedBy()));
    }

    public void hide(long commentId, Instant now) {
        comments.findById(commentId).orElseThrow().hideByStaff(now);
    }

    public void restore(long commentId) {
        comments.findById(commentId).orElseThrow().restore();
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<Long> postId(long commentId) {
        return comments.findById(commentId).map(FeedComment::getPostId);
    }

}
