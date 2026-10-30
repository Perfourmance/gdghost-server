package com.gdghost.notice;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 신고 접수 · 처리에서 공지 댓글을 다루는 길. lock으로 댓글 행을 먼저 잠근 뒤 hide · restore를 부른다 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NoticeCommentModeration {

    /** deletedBy는 null · AUTHOR · STAFF */
    public record Content(long authorId, String deletedBy) {
    }

    private final NoticeCommentRepository comments;
    private final NoticeRepository notices;

    public NoticeCommentModeration(NoticeCommentRepository comments, NoticeRepository notices) {
        this.comments = comments;
        this.notices = notices;
    }

    /** 지운 댓글 · 숨긴 댓글, 공지가 지워진 댓글은 없는 것으로 본다 */
    public boolean isLive(long commentId) {
        return comments.findById(commentId)
                .filter(c -> !c.isDeleted())
                .flatMap(c -> notices.findById(c.getNoticeId()))
                .filter(notice -> !notice.isDeleted())
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
    public Optional<Long> noticeId(long commentId) {
        return comments.findById(commentId).map(NoticeComment::getNoticeId);
    }

}
