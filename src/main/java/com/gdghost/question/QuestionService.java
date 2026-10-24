package com.gdghost.question;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

/** 질문 작성 · 목록 · 상세 */
@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final QuestionAnonAliasWriter aliasWriter;
    private final QuestionListReader listReader;
    private final QuestionDetailReader detailReader;
    private final QuestionViewCounter viewCounter;
    private final Clock clock;

    public QuestionService(QuestionRepository questions, QuestionAnonAliasWriter aliasWriter,
            QuestionListReader listReader, QuestionDetailReader detailReader, QuestionViewCounter viewCounter,
            Clock clock) {
        this.questions = questions;
        this.aliasWriter = aliasWriter;
        this.listReader = listReader;
        this.detailReader = detailReader;
        this.viewCounter = viewCounter;
        this.clock = clock;
    }

    // TODO 디스코드(5) · 전체 앱 내 알림(6) · 임베딩 · 선배 지목(7)
    @Transactional
    public QuestionCreatedResponse create(long authorId, QuestionCreateRequest request) {
        Question question = questions.save(Question.write(authorId, request.category(), request.title(),
                request.body(), request.isAnonymous(), clock.instant()));
        if (question.isAnonymous()) {
            aliasWriter.assignAuthor(question.getId(), authorId);
        }
        return new QuestionCreatedResponse(detail(find(question.getId()), authorId), null);
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
                AnswerTree.build(detailReader.answers(question.id()), question.adoptedAnswerId(), viewerId), null);
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
