package com.gdghost.notice;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공지 댓글. 익명 · 답글 · 수정 없음 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long noticeId;
    private long authorId;
    private String body;
    private Instant createdAt;
    private Instant deletedAt;
    private String deletedBy;

    static NoticeComment write(long noticeId, long authorId, String body, Instant now) {
        NoticeComment comment = new NoticeComment();
        comment.noticeId = noticeId;
        comment.authorId = authorId;
        comment.body = body;
        comment.createdAt = now;
        return comment;
    }

    void deleteByAuthor(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "AUTHOR";
    }

    /** 신고 처리의 숨김 */
    void hideByStaff(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "STAFF";
    }

    /** 숨김 되돌리기 — 작성자가 지운 것은 부르는 쪽에서 막는다 */
    void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

}
