package com.gdghost.auth;

/** 개발자(어드민)가 부원 이메일 변경을 요청함 */
public record EmailChangeRequestedEvent(long requesterId, long memberId) {
}
