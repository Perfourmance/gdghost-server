package com.gdghost.report;

import java.time.Instant;

/** 신고 목록 항목. 대상 작성자는 담지 않는다 (익명 글 보호) */
public record OpsReportResponse(
        long id,
        ReportTargetType targetType,
        long targetId,
        String targetPreview,
        String targetLink,
        String targetDeletedBy,
        String reason,
        int sameTargetCount,
        Person reporter,
        ReportState state,
        Instant createdAt,
        Person handledBy,
        Instant handledAt) {

    /** 탈퇴자는 id null · '탈퇴한 부원' */
    public record Person(Long id, String nickname) {
    }

}
