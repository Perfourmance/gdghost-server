package com.gdghost.question;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 처리의 숨김 · 되돌리기. lock으로 질문 행을 먼저 잠근 뒤 hide · restore를 부른다
 * (답변 작성 · 삭제 · 채택과 한 줄로 서도록).
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ContentModeration {

    /** deletedBy는 null · AUTHOR · STAFF */
    public record Content(long authorId, boolean anonymous, String deletedBy) {
    }

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final QuestionDetailReader detailReader;

    public ContentModeration(QuestionRepository questions, AnswerRepository answers,
            QuestionDetailReader detailReader) {
        this.questions = questions;
        this.answers = answers;
        this.detailReader = detailReader;
    }

    public Optional<Content> lockQuestion(long questionId) {
        return questions.findForUpdate(questionId)
                .map(q -> new Content(q.getAuthorId(), q.isAnonymous(), q.getDeletedBy()));
    }

    public Optional<Content> lockAnswer(long answerId) {
        return answers.findQuestionId(answerId)
                .flatMap(questions::findForUpdate)
                .flatMap(q -> answers.findById(answerId))
                .map(a -> new Content(a.getAuthorId(), a.isAnonymous(), a.getDeletedBy()));
    }

    public void hideQuestion(long questionId, Instant now) {
        questions.findById(questionId).orElseThrow().hideByStaff(now);
    }

    public void restoreQuestion(long questionId) {
        questions.findById(questionId).orElseThrow().restore();
    }

    /** 답변 삭제(#37)와 같은 상태 재계산 — 채택이면 풀고, 남은 답변이 없으면 WAITING */
    public void hideAnswer(long answerId, Instant now) {
        Answer answer = answers.findById(answerId).orElseThrow();
        answer.hideByStaff(now);
        answers.flush();
        questions.findById(answer.getQuestionId()).orElseThrow()
                .answerDeleted(answerId, detailReader.hasLiveTopLevelAnswer(answer.getQuestionId()));
    }

    /** 채택은 다시 걸지 않는다 */
    public void restoreAnswer(long answerId) {
        Answer answer = answers.findById(answerId).orElseThrow();
        answer.restore();
        if (!answer.isReply()) {
            questions.findById(answer.getQuestionId()).orElseThrow().markAnswered();
        }
    }

}
