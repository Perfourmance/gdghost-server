package com.gdghost.report;

import java.time.Clock;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.notice.NoticeCommentModeration;
import com.gdghost.question.AnswerRepository;
import com.gdghost.question.QuestionRepository;
import com.gdghost.share.ShareModeration;

/** 신고 접수. 작성자 · 신고자에게 알리지 않는다 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final NoticeCommentModeration noticeComments;
    private final ShareModeration shares;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReportService(ReportRepository reports, QuestionRepository questions, AnswerRepository answers,
            NoticeCommentModeration noticeComments, ShareModeration shares, ApplicationEventPublisher events,
            Clock clock) {
        this.reports = reports;
        this.questions = questions;
        this.answers = answers;
        this.noticeComments = noticeComments;
        this.shares = shares;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ReportCreatedResponse create(long reporterId, ReportCreateRequest request) {
        ReportTargetType type = request.targetType();
        if (!type.isSupported()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("targetType", "아직 신고할 수 없는 대상입니다.")));
        }
        if (!isLive(type, request.targetId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "신고할 글을 찾을 수 없습니다.");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, type, request.targetId())) {
            throw alreadyReported();
        }
        try {
            Report report = reports.saveAndFlush(
                    Report.open(reporterId, type, request.targetId(), request.reason(), clock.instant()));
            events.publishEvent(new ReportCreatedEvent(report.getId()));
            return new ReportCreatedResponse(report.getId());
        } catch (DataIntegrityViolationException e) {
            throw alreadyReported();
        }
    }

    /** 지운 글 · 숨긴 글, 질문 · 공지가 지워진 답변 · 댓글은 없는 것으로 본다 */
    private boolean isLive(ReportTargetType type, long targetId) {
        return switch (type) {
            case QUESTION -> questions.findById(targetId).filter(q -> !q.isDeleted()).isPresent();
            case ANSWER -> answers.findById(targetId)
                    .filter(a -> !a.isDeleted())
                    .flatMap(a -> questions.findById(a.getQuestionId()))
                    .filter(q -> !q.isDeleted())
                    .isPresent();
            case NOTICE_COMMENT -> noticeComments.isLive(targetId);
            case SHARE -> shares.isLive(targetId);
            default -> false;
        };
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(ErrorCode.ALREADY_REPORTED, "이미 신고했어요.");
    }

}
