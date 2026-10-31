package com.gdghost.member;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * targetId는 질문 id (답변도 그 질문으로 이동), 공유면 공유 id.
 * anonymous는 본인 조회일 때만 내려간다 — 남이 볼 때는 익명 글이 아예 빠진다.
 */
public record ActivityItem(ActivityType type, long targetId, String title, String snippet, boolean adopted,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean anonymous, Instant createdAt) {

    /** 커서용 — 답변이면 답변 id, 질문이면 질문 id */
    record Row(long id, ActivityItem item) {
    }

}
