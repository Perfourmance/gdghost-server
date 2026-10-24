package com.gdghost.question;

import java.time.Instant;

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

}
