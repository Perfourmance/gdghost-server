package com.gdghost.question;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.ratelimit.RateLimitedException;

/** 답변 · 답글 작성 · 수정 · 삭제 */
@Service
public class AnswerService {

    static final int MIN_ANSWER_LENGTH = 5;
    static final Duration MIN_INTERVAL = Duration.ofSeconds(5);

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final QuestionAnonAliasWriter aliasWriter;
    private final QuestionDetailReader detailReader;
    private final QuestionNotifier notifier;
    private final RoutingPort routing;
    private final Clock clock;

    public AnswerService(QuestionRepository questions, AnswerRepository answers, QuestionAnonAliasWriter aliasWriter,
            QuestionDetailReader detailReader, QuestionNotifier notifier, RoutingPort routing, Clock clock) {
        this.questions = questions;
        this.answers = answers;
        this.aliasWriter = aliasWriter;
        this.detailReader = detailReader;
        this.notifier = notifier;
        this.routing = routing;
        this.clock = clock;
    }

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
        checkLength(parent == null, request.body());

        if (request.isAnonymous()) {
            aliasWriter.assign(questionId, memberId);
        }
        Answer answer = answers.save(Answer.write(questionId, parent, memberId, request.body(),
                request.isAnonymous(), now));
        if (parent == null) {
            question.markAnswered();
            routing.answered(questionId, memberId);
        }
        questions.flush();
        QuestionDetailReader.AnswerRow row = detailReader.answer(answer.getId());
        notifier.answerCreated(question, parent, row);
        return AnswerTree.view(row, answer.getDepth(), question.getAdoptedAnswerId(),
                memberId, List.of());
    }

    /** 본문만. 채택은 유지, 알림 없음. 응답은 질문 상세 트리의 그 노드(하위 답글 포함) */
    @Transactional
    public AnswerNode update(long memberId, long answerId, AnswerUpdateRequest request) {
        Answer answer = liveAnswer(answerId);
        Question question = liveQuestion(questions.findById(answer.getQuestionId()));
        checkAuthor(answer, memberId);
        checkLength(!answer.isReply(), request.body());
        answer.edit(request.body(), clock.instant());
        answers.flush();
        return AnswerTree.find(AnswerTree.build(detailReader.answers(question.getId()), question.getAdoptedAnswerId(),
                memberId), answerId).orElseThrow();
    }

    /** 답글이 남으면 트리에 자리가 남는다. 채택된 답변이면 채택도 풀고, 남은 답변이 없으면 WAITING */
    @Transactional
    public void delete(long memberId, long answerId) {
        Answer answer = liveAnswer(answerId);
        Question question = liveQuestion(questions.findForUpdate(answer.getQuestionId()));
        checkAuthor(answer, memberId);
        answer.deleteByAuthor(clock.instant());
        answers.flush();
        question.answerDeleted(answerId, detailReader.hasLiveTopLevelAnswer(question.getId()));
    }

    /** 답변 · 질문 중 하나라도 없거나 지웠으면 404, 그다음 남의 답변이면 403 */
    private Answer liveAnswer(long answerId) {
        return answers.findById(answerId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));
    }

    private static Question liveQuestion(Optional<Question> question) {
        return question.filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다."));
    }

    private static void checkAuthor(Answer answer, long memberId) {
        if (answer.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 답변만 고치거나 지울 수 있습니다.");
        }
    }

    private static void checkLength(boolean topLevel, String body) {
        if (topLevel && body.length() < MIN_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("body", "답변은 " + MIN_ANSWER_LENGTH + "자 이상 적어 주세요.")));
        }
    }

}
