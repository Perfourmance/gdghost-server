package com.gdghost.question;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** parentId가 없으면 답변, 있으면 답글 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long questionId;
    private Long parentId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private int depth;

    private long authorId;
    private String body;
    private Instant editedAt;

    @Column(name = "is_anonymous")
    private boolean anonymous;

    private Instant createdAt;
    private Instant deletedAt;
    private String deletedBy;

    /** depth는 부모 + 1, 3에서 멈춘다 */
    public static Answer write(long questionId, Answer parent, long authorId, String body, boolean anonymous,
            Instant now) {
        Answer answer = new Answer();
        answer.questionId = questionId;
        answer.parentId = parent == null ? null : parent.id;
        answer.depth = parent == null ? 0 : Math.min(parent.depth + 1, AnswerTree.MAX_DEPTH);
        answer.authorId = authorId;
        answer.body = body;
        answer.anonymous = anonymous;
        answer.createdAt = now;
        return answer;
    }

    /** 본문이 실제로 바뀔 때만 수정 시각을 찍는다 */
    public void edit(String body, Instant now) {
        if (!body.equals(this.body)) {
            this.body = body;
            this.editedAt = now;
        }
    }

    public void deleteByAuthor(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "AUTHOR";
    }

    /** 신고 처리의 숨김 */
    public void hideByStaff(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "STAFF";
    }

    /** 숨김 되돌리기 — 작성자가 지운 것은 부르는 쪽에서 막는다 */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

}
