package com.gdghost.report;

/** 신고 대상. 기능이 아직 없는 단계의 종류는 400 */
public enum ReportTargetType {
    QUESTION, ANSWER, SHARE, SHARE_NOTE, FEED, FEED_COMMENT, NOTICE_COMMENT;

    // TODO 덧붙이기 · 피드 · 피드 댓글(9)
    boolean isSupported() {
        return this == QUESTION || this == ANSWER || this == NOTICE_COMMENT || this == SHARE;
    }
}
