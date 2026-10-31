package com.gdghost.question;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

/** 질문 작성 · 목록 · 상세 · 수정 · 삭제 */
@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final QuestionAnonAliasWriter aliasWriter;
    private final QuestionListReader listReader;
    private final QuestionDetailReader detailReader;
    private final QuestionViewCounter viewCounter;
    private final QuestionNotifier notifier;
    private final RoutingPort routing;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public QuestionService(QuestionRepository questions, QuestionAnonAliasWriter aliasWriter,
            QuestionListReader listReader, QuestionDetailReader detailReader, QuestionViewCounter viewCounter,
            QuestionNotifier notifier, RoutingPort routing, ApplicationEventPublisher events, Clock clock) {
        this.questions = questions;
        this.aliasWriter = aliasWriter;
        this.listReader = listReader;
        this.detailReader = detailReader;
        this.viewCounter = viewCounter;
        this.notifier = notifier;
        this.routing = routing;
        this.events = events;
        this.clock = clock;
    }

    /** 지목한 선배를 쓸 수 없으면 실패시키지 않고 FALLBACK — 쓴 글을 잃지 않게 */
    @Transactional
    public QuestionCreatedResponse create(long authorId, QuestionCreateRequest request) {
        if (request.targetMemberId() != null && request.targetMemberId() == authorId) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("targetMemberId", "본인을 지목할 수 없습니다.")));
        }
        Question question = questions.save(Question.write(authorId, request.category(), request.title(),
                request.body(), request.isAnonymous(), clock.instant()));
        if (question.isAnonymous()) {
            aliasWriter.assignAuthor(question.getId(), authorId);
        }
        notifier.questionCreated(question);
        events.publishEvent(new QuestionCreatedEvent(question.getId()));
        DirectDelivery direct = request.targetMemberId() == null ? null
                : routing.direct(question, request.targetMemberId());
        return new QuestionCreatedResponse(detail(find(question.getId()), authorId), direct);
    }

    /** 비회원(me == null)은 게스트 모양 */
    @Transactional(readOnly = true)
    public CursorPage<?> list(AuthMember me, QuestionCategory category, boolean mine, boolean unanswered,
            String cursor, int size) {
        if (me == null && mine) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "내 질문은 로그인 후 볼 수 있습니다.");
        }
        List<QuestionListReader.Row> rows = listReader.list(category, mine ? me.id() : null, unanswered,
                afterId(cursor), size + 1);
        CursorPage<QuestionListReader.Row> page = CursorPage.of(rows, size, row -> String.valueOf(row.id()));
        if (page.items().isEmpty()) {
            return page;
        }
        List<Long> ids = page.items().stream().map(QuestionListReader.Row::id).toList();
        Map<Long, Long> answerCounts = listReader.answerCounts(ids);
        if (me == null) {
            return new CursorPage<>(page.items().stream()
                    .map(row -> new GuestQuestionSummary(row.id(), row.category(), row.title(),
                            GuestAuthor.of(row.author(), row.anonymous()), row.createdAt(),
                            answerCounts.getOrDefault(row.id(), 0L)))
                    .toList(), page.nextCursor(), page.hasMore());
        }
        Map<Long, List<QuestionListReader.Answerer>> answerers = listReader.answerers(ids);
        return new CursorPage<>(page.items().stream()
                .map(row -> new QuestionSummary(row.id(), row.category(), row.title(),
                        AuthorRef.of(row.author(), row.anonymous(), AnonLabels.QUESTION_AUTHOR, me.id()),
                        row.state(), answerCounts.getOrDefault(row.id(), 0L),
                        answerers.getOrDefault(row.id(), List.of()).stream()
                                .map(answerer -> AuthorRef.of(answerer.author(), answerer.anonymous(),
                                        AnonLabels.answer(answerer.aliasNo()), me.id()))
                                .toList(),
                        row.viewCount(), row.createdAt()))
                .toList(), page.nextCursor(), page.hasMore());
    }

    /** 글쓴이 본인 조회는 조회수에 세지 않는다 */
    @Transactional
    public QuestionDetail detail(long viewerId, long questionId) {
        QuestionDetailReader.QuestionRow question = find(questionId);
        if (question.author().id() != viewerId && viewCounter.firstViewToday(viewerId, questionId)) {
            questions.incrementViewCount(questionId);
            question = find(questionId);
        }
        return detail(question, viewerId);
    }

    /** 답변(지우지 않은 답변 · 답글)이 있을 때 고치면 수정됨 표시. TODO 라우팅 후보 재계산(7) */
    @Transactional
    public QuestionDetail update(long memberId, long questionId, QuestionUpdateRequest request) {
        Question question = mine(memberId, questionId);
        boolean changed = question.edit(request.category(), request.title(), request.body(),
                detailReader.hasLiveAnswer(questionId), clock.instant());
        questions.flush();
        if (changed) {
            questions.deleteEmbedding(questionId);
            events.publishEvent(new QuestionEditedEvent(questionId));
        }
        return detail(find(questionId), memberId);
    }

    /** 답변은 남기지만 질문과 함께 안 보인다. TODO 보내지 않은 라우팅 취소(7) */
    @Transactional
    public void delete(long memberId, long questionId) {
        mine(memberId, questionId).deleteByAuthor(clock.instant());
    }

    /** 없거나 삭제 · 숨김이면 404, 남의 질문이면 403 */
    private Question mine(long memberId, long questionId) {
        Question question = questions.findById(questionId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다."));
        if (question.getAuthorId() != memberId) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR, "본인 질문만 고치거나 지울 수 있습니다.");
        }
        return question;
    }

    private QuestionDetailReader.QuestionRow find(long questionId) {
        return detailReader.find(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "질문을 찾을 수 없습니다."));
    }

    private QuestionDetail detail(QuestionDetailReader.QuestionRow question, long viewerId) {
        boolean mine = question.author().id() == viewerId;
        return new QuestionDetail(question.id(), question.category(), question.title(), question.body(),
                question.anonymous(),
                AuthorRef.of(question.author(), question.anonymous(), AnonLabels.QUESTION_AUTHOR, viewerId),
                question.state(), question.adoptedAnswerId(), question.editedAfterAnswer(), question.viewCount(),
                question.createdAt(), question.updatedAt() != null ? question.updatedAt() : question.createdAt(),
                mine, !mine, question.anonymous(),
                AnswerTree.build(detailReader.answers(question.id()), question.adoptedAnswerId(), viewerId),
                routing.view(question.id(), question.anonymous(), viewerId));
    }

    private static Long afterId(String cursor) {
        String raw = CursorPage.decode(cursor);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            throw CursorPage.invalidCursor();
        }
    }

}
