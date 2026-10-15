package com.gdghost.auth;

import com.gdghost.common.security.Role;
import com.gdghost.member.ProfileCompleteness;

/** 가입 완료 · 로그인 · 내 세션 확인이 같이 쓰는 응답 */
public record MeResponse(long id, String loginId, String nickname, int generation, Role role, String staffTitle,
        int unreadNotificationCount, int unconfirmedMustReadCount, ProfileCompleteness profileCompleteness) {
}
