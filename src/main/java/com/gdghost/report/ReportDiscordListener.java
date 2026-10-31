package com.gdghost.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gdghost.common.discord.DiscordChannel;
import com.gdghost.common.discord.DiscordMessage;
import com.gdghost.common.discord.DiscordNotifier;
import com.gdghost.common.event.DomainEventListener;
import com.gdghost.feed.FeedCommentModeration;
import com.gdghost.notice.NoticeCommentModeration;
import com.gdghost.question.AnswerRepository;
import com.gdghost.share.ShareNoteModeration;

/** 신고 접수 → 운영진 서버. 대상 종류 · 사유 · 링크, 신고자는 보내지 않는다 */
@Component
public class ReportDiscordListener {

    private final ReportRepository reports;
    private final AnswerRepository answers;
    private final NoticeCommentModeration noticeComments;
    private final ShareNoteModeration shareNotes;
    private final FeedCommentModeration feedComments;
    private final DiscordNotifier discord;
    private final String webUrl;

    public ReportDiscordListener(ReportRepository reports, AnswerRepository answers,
            NoticeCommentModeration noticeComments, ShareNoteModeration shareNotes, FeedCommentModeration feedComments, DiscordNotifier discord,
            @Value("${web.url}") String webUrl) {
        this.shareNotes = shareNotes;
        this.feedComments = feedComments;
        this.reports = reports;
        this.answers = answers;
        this.noticeComments = noticeComments;
        this.discord = discord;
        this.webUrl = webUrl;
    }

    @DomainEventListener
    public void on(ReportCreatedEvent event) {
        reports.findById(event.reportId()).ifPresent(report -> discord.send(DiscordChannel.STAFF,
                new DiscordMessage("신고 접수 · " + label(report.getTargetType()), link(report),
                        "사유 " + DiscordMessage.escape(report.getReason()))));
    }

    private String link(Report report) {
        long id = report.getTargetId();
        return switch (report.getTargetType()) {
            case QUESTION -> webUrl + "/questions/" + id;
            case ANSWER -> answers.findQuestionId(id)
                    .map(questionId -> webUrl + "/questions/" + questionId + "#answer-" + id)
                    .orElse(null);
            case NOTICE_COMMENT -> noticeComments.noticeId(id)
                    .map(noticeId -> webUrl + "/notices/" + noticeId + "#comment-" + id)
                    .orElse(null);
            case SHARE -> webUrl + "/shares/" + id;
            case SHARE_NOTE -> shareNotes.shareId(id)
                    .map(shareId -> webUrl + "/shares/" + shareId + "#note-" + id)
                    .orElse(null);
            case FEED -> webUrl + "/feed/" + id;
            case FEED_COMMENT -> feedComments.postId(id)
                    .map(postId -> webUrl + "/feed/" + postId + "#comment-" + id)
                    .orElse(null);
        };
    }

    private static String label(ReportTargetType type) {
        return switch (type) {
            case QUESTION -> "질문";
            case ANSWER -> "답변";
            case SHARE -> "공유";
            case SHARE_NOTE -> "덧붙이기";
            case FEED -> "피드";
            case FEED_COMMENT -> "피드 댓글";
            case NOTICE_COMMENT -> "공지 댓글";
        };
    }

}
