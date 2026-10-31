package com.gdghost.report;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.audit.OpsAudit;
import com.gdghost.common.audit.OpsAuditLogger;
import com.gdghost.common.audit.OpsTargetType;
import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.page.OpsPage;
import com.gdghost.feed.FeedCommentModeration;
import com.gdghost.feed.FeedModeration;
import com.gdghost.notice.NoticeCommentModeration;
import com.gdghost.question.ContentModeration;
import com.gdghost.question.ContentModeration.Content;
import com.gdghost.share.ShareModeration;
import com.gdghost.share.ShareNoteModeration;

/**
 * 운영 신고 목록 · 처리. 처리는 같은 대상의 신고를 함께 닫고, 작성자에게 알리지 않는다.
 */
@Service
public class OpsReportService {

    private static final String STAFF = "STAFF";

    private final ReportRepository reports;
    private final OpsReportReader reader;
    private final ContentModeration moderation;
    private final NoticeCommentModeration noticeComments;
    private final ShareModeration shares;
    private final ShareNoteModeration shareNotes;
    private final FeedModeration feeds;
    private final FeedCommentModeration feedComments;
    private final OpsAuditLogger opsAudit;
    private final Clock clock;

    public OpsReportService(ReportRepository reports, OpsReportReader reader, ContentModeration moderation,
            NoticeCommentModeration noticeComments, ShareModeration shares, ShareNoteModeration shareNotes,
            FeedModeration feeds, FeedCommentModeration feedComments, OpsAuditLogger opsAudit, Clock clock) {
        this.feedComments = feedComments;
        this.feeds = feeds;
        this.shareNotes = shareNotes;
        this.shares = shares;
        this.reports = reports;
        this.reader = reader;
        this.moderation = moderation;
        this.noticeComments = noticeComments;
        this.opsAudit = opsAudit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OpsPage<OpsReportResponse> list(ReportState state, int page, int size) {
        return reader.list(state, page, size);
    }

    /**
     * DISMISS · HIDE는 OPEN에서만, RESTORE는 HIDDEN에서만 — 아니면 409.
     * 작성자가 먼저 지운 글은 HIDE해도 그대로 두고, 되돌릴 수 없다.
     */
    @Transactional
    public OpsReportResponse handle(long staffId, long reportId, ReportAction action) {
        ReportTarget target = reports.findTarget(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다."));
        // 대상(질문 · 댓글 행) → 신고 순서로 잠근다
        Content content = lock(target).orElseThrow();
        List<Report> group = reports.lockByTarget(target.type(), target.id());
        Report report = group.stream().filter(r -> r.getId() == reportId).findFirst().orElseThrow();

        ReportState from = action == ReportAction.RESTORE ? ReportState.HIDDEN : ReportState.OPEN;
        if (report.getState() != from) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED, "이미 처리된 신고입니다.");
        }
        Instant now = clock.instant();
        ReportState to = switch (action) {
            case DISMISS -> ReportState.DISMISSED;
            case HIDE -> {
                if (content.deletedBy() == null) {
                    hide(target, now);
                }
                yield ReportState.HIDDEN;
            }
            case RESTORE -> {
                if (!STAFF.equals(content.deletedBy())) {
                    throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED, "작성자가 지운 글은 되돌릴 수 없습니다.");
                }
                restore(target);
                yield ReportState.RESTORED;
            }
        };
        List<Report> closing = group.stream().filter(r -> r.getState() == from).toList();
        closing.forEach(r -> r.close(to, staffId, now));

        OpsAudit audit = OpsAudit.of(staffId, auditAction(action))
                .target(OpsTargetType.REPORT, reportId)
                .detail(Map.of("targetType", target.type().name(), "targetId", target.id(),
                        "closedReports", closing.size()));
        // 익명 글은 운영 기록에도 작성자를 남기지 않는다 (F2-05)
        opsAudit.record(content.anonymous() ? audit : audit.targetMember(content.authorId()));

        reports.flush();
        return reader.find(reportId).orElseThrow();
    }

    private Optional<Content> lock(ReportTarget target) {
        return switch (target.type()) {
            case QUESTION -> moderation.lockQuestion(target.id());
            case ANSWER -> moderation.lockAnswer(target.id());
            case NOTICE_COMMENT -> noticeComments.lock(target.id())
                    .map(c -> new Content(c.authorId(), false, c.deletedBy()));
            case SHARE -> shares.lock(target.id()).map(s -> new Content(s.authorId(), false, s.deletedBy()));
            case SHARE_NOTE -> shareNotes.lock(target.id()).map(n -> new Content(n.authorId(), false, n.deletedBy()));
            case FEED -> feeds.lock(target.id()).map(p -> new Content(p.authorId(), false, p.deletedBy()));
            case FEED_COMMENT -> feedComments.lock(target.id())
                    .map(c -> new Content(c.authorId(), false, c.deletedBy()));
        };
    }

    private void hide(ReportTarget target, Instant now) {
        switch (target.type()) {
            case QUESTION -> moderation.hideQuestion(target.id(), now);
            case ANSWER -> moderation.hideAnswer(target.id(), now);
            case NOTICE_COMMENT -> noticeComments.hide(target.id(), now);
            case SHARE -> shares.hide(target.id(), now);
            case SHARE_NOTE -> shareNotes.hide(target.id(), now);
            case FEED -> feeds.hide(target.id(), now);
            case FEED_COMMENT -> feedComments.hide(target.id(), now);
        }
    }

    private void restore(ReportTarget target) {
        switch (target.type()) {
            case QUESTION -> moderation.restoreQuestion(target.id());
            case ANSWER -> moderation.restoreAnswer(target.id());
            case NOTICE_COMMENT -> noticeComments.restore(target.id());
            case SHARE -> shares.restore(target.id());
            case SHARE_NOTE -> shareNotes.restore(target.id());
            case FEED -> feeds.restore(target.id());
            case FEED_COMMENT -> feedComments.restore(target.id());
        }
    }

    private static OpsAction auditAction(ReportAction action) {
        return switch (action) {
            case DISMISS -> OpsAction.REPORT_DISMISS;
            case HIDE -> OpsAction.REPORT_HIDE;
            case RESTORE -> OpsAction.REPORT_RESTORE;
        };
    }

}
