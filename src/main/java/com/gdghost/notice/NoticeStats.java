package com.gdghost.notice;

/** N명 중 M명 확인. 탈퇴 · 정지한 사람은 N에도 M에도 넣지 않는다 */
public record NoticeStats(long memberCount, long readCount, long confirmedCount) {
}
