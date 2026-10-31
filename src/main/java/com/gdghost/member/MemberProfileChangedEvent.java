package com.gdghost.member;

/** 가입 · 프로필 수정. 매칭 재료가 실제로 바뀌었는지는 받는 쪽이 해시로 본다 */
public record MemberProfileChangedEvent(long memberId) {
}
