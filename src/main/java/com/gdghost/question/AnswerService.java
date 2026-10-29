package com.gdghost.question;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.ratelimit.RateLimitedException;

/** 답변 · 답글 */
@Service
public class AnswerService {

    static final int MIN_ANSWER_LENGTH = 5;
    static final Duration MIN_INTERVAL = Duration.ofSeconds(5);

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final QuestionAnonAliasWriter aliasWriter;
    private final QuestionDetailReader detailReader;
    private final Clock clock;

    public AnswerService(QuestionRepository questions, AnswerRepository answers, QuestionAnonAliasWriter aliasWriter,
            QuestionDetailReader detailReader, Clock clock) {
        this.questions = questions;
        this.answers = answers;
        this.aliasWriter = aliasWriter;
        this.detailReader = detailReader;
        this.clock = clock;
    }

    // TODO 질문자 · 부모 작성자 알림 + 웹 푸시(6) · 지목된 routing ANSWERED(7)
    @Transactional
    public AnswerNode create(long memberId, long questionId, AnswerCreateRequest request) {
        Instant now = clock.instant();
        // 저장된 마지막 제출 기준 — 실패한 시도는 세지 않는다
        Instant last = answers.lastCreatedAt(memberId);
        if (last != null && now.isBefore(last.plus(MIN_INTERVAL))) {
            long millis = Duration.between(now, last.plus(MIN_INTERVAL)).toMillis();
            throw new RateLimitedException(ErrorCode.ANSWER_TOO_FAST, Math.max(1, (millis + 999) / 1000));
        }

        Question question = questions.findForUpdate(questionId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다."));
        Answer parent = request.parentId() == null ? null : answers.findById(request.parentId())
                .filter(found -> found.getQuestionId() == questionId && !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "답글을 달 답변을 찾을 수 없습니다."));
        if (parent == null && question.getAuthorId() == memberId) {
            throw new BusinessException(ErrorCode.OWN_QUESTION_ANSWER, "내 질문에는 답글만 달 수 있습니다.");
        }
        if (request.isAnonymous() && !question.isAnonymous()) {
            throw new BusinessException(ErrorCode.ANONYMOUS_NOT_ALLOWED, "익명 질문에서만 익명으로 답할 수 있습니다.");
        }
        if (parent == null && request.body().length() < MIN_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("body", "답변은 " + MIN_ANSWER_LENGTH + "자 이상 적어 주세요.")));
        }

        if (request.isAnonymous()) {
            aliasWriter.assign(questionId, memberId);
        }
        Answer answer = answers.save(Answer.write(questionId, parent, memberId, request.body(),
                request.isAnonymous(), now));
        if (parent == null) {
            question.markAnswered();
        }
        questions.flush();
        return AnswerTree.view(detailReader.answer(answer.getId()), answer.getDepth(), question.getAdoptedAnswerId(),
                memberId, List.of());
    }

}
