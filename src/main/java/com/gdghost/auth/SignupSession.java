package com.gdghost.auth;

import java.time.Instant;

/**
 * 가입 진행 상태. verifiedEmail은 인증번호 확인이 끝난 메일 — 가입 완료 때 이 메일만 쓴다.
 */
public record SignupSession(String token, long inviteCodeId, int generation, String verifiedEmail,
        Instant expiresAt) {

    SignupSession withVerifiedEmail(String email) {
        return new SignupSession(token, inviteCodeId, generation, email, expiresAt);
    }

}
