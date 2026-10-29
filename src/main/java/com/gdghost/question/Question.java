package com.gdghost.question;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long authorId;

    @Enumerated(EnumType.STRING)
    private QuestionCategory category;

    private String title;
    private String body;

    @Column(name = "is_anonymous")
    private boolean anonymous;

    @Enumerated(EnumType.STRING)
    private QuestionState state;

    private Long adoptedAnswerId;
    private boolean editedAfterAnswer;
    private int viewCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String deletedBy;

    /** 익명 여부는 나중에 바꿀 수 없다 */
    public static Question write(long authorId, QuestionCategory category, String title, String body,
            boolean anonymous, Instant now) {
        Question question = new Question();
        question.authorId = authorId;
        question.category = category;
        question.title = title;
        question.body = body;
        question.anonymous = anonymous;
        question.state = QuestionState.WAITING;
        question.createdAt = now;
        return question;
    }

    /** null은 그대로, 빈 본문은 지운다. 실제로 바뀐 게 있을 때만 수정 시각을 찍는다 */
    public void edit(QuestionCategory category, String title, String body, boolean answered, Instant now) {
        QuestionCategory newCategory = category != null ? category : this.category;
        String newTitle = title != null ? title : this.title;
        String newBody = body == null ? this.body : body.isEmpty() ? null : body;
        if (newCategory == this.category && newTitle.equals(this.title) && Objects.equals(newBody, this.body)) {
            return;
        }
        this.category = newCategory;
        this.title = newTitle;
        this.body = newBody;
        this.updatedAt = now;
        if (answered) {
            this.editedAfterAnswer = true;
        }
    }

    /** 첫 답변이면 WAITING → ANSWERED */
    public void markAnswered() {
        if (state == QuestionState.WAITING) {
            state = QuestionState.ANSWERED;
        }
    }

    public void deleteByAuthor(Instant now) {
        this.deletedAt = now;
        this.deletedBy = "AUTHOR";
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

}
