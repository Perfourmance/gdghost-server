package com.gdghost.feed;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 피드 댓글(depth 0) · 답글(depth 1). 익명 · 수정 없음 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long postId;
    private Long parentId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int depth;

    private long authorId;
    private String body;
    private Instant createdAt;
    private Instant deletedAt;
    private String deletedBy;

    static FeedComment write(long postId, Long parentId, long authorId, String body, Instant now) {
        FeedComment comment = new FeedComment();
        comment.postId = postId;
        comment.parentId = parentId;
        comment.depth = parentId == null ? 0 : 1;
        comment.authorId = authorId;
        comment.body = body;
        comment.createdAt = now;
        return comment;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

    boolean isReply() {
        return parentId != null;
    }

    void deleteByAuthor(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "AUTHOR";
    }

    void hideByStaff(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "STAFF";
    }

    void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

}
