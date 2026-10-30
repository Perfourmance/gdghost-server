package com.gdghost.auth;

/** 어드민 요청 이메일 변경이 링크로 반영됨. requesterId는 요청 기록이 없으면 null */
public record EmailChangedEvent(Long requesterId, long memberId) {
}
