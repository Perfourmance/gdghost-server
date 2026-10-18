package com.gdghost.member;

/** 익명 · 삭제 · 답글은 세지 않는다 (본인 조회도 동일) */
public record MemberStats(long answerCount, long questionCount, long shareCount, long feedCount) {
}
