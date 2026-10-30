package com.gdghost.notice;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

/** 공지 댓글 작성 · 삭제. 댓글 알림은 보내지 않는다 (운영진은 상세에서 확인) */
@Service
public class NoticeCommentService {

    private final NoticeCommentRepository comments;
    private final NoticeRepository notices;
    private final NoticeCommentReader reader;
    private final Clock clock;

    public NoticeCommentService(NoticeCommentRepository comments, NoticeRepository notices,
            NoticeCommentReader reader, Clock clock) {
        this.comments = comments;
        this.notices = notices;
        this.reader = reader;
        this.clock = clock;
    }

    @Transactional
    public NoticeDetail.Comment create(long memberId, long noticeId, NoticeCommentCreateRequest request) {
        if (!isLiveNotice(noticeId)) {
            throw notFound("공지를 찾을 수 없습니다.");
        }
        NoticeComment comment = comments.saveAndFlush(
                NoticeComment.write(noticeId, memberId, request.body(), clock.instant()));
        return reader.find(comment.getId(), memberId);
    }

    /** 본인 것만 (운영진도 남의 댓글은 신고 처리의 숨김으로만). 지웠거나 공지가 지워졌으면 404 */
    @Transactional
    public void delete(long memberId, long commentId) {
        NoticeComment comment = comments.findForUpdate(commentId)
                .filter(c -> !c.isDeleted() && isLiveNotice(c.getNoticeId()))
                .orElseThrow(() -> notFound("댓글을 찾을 수 없습니다."));
        if (comment.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 댓글만 지울 수 있습니다.");
        }
        comment.deleteByAuthor(clock.instant());
    }

    private boolean isLiveNotice(long noticeId) {
        return notices.findById(noticeId).filter(notice -> !notice.isDeleted()).isPresent();
    }

    private static BusinessException notFound(String detail) {
        return new BusinessException(ErrorCode.NOT_FOUND, detail);
    }

}
