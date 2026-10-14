package com.gdghost.auth;

import java.time.Instant;

/**
 * 가입 진행 상태. verifiedEmail은 인증번호 확인이 끝난 메일 — 가입 완료 때 이 메일만 쓴다.
 * emailSendCount는 이 토큰으로 보낸 인증 메일 수 — 메일 주소를 바꿔 가며 서버 한도를 소진하지 못하게.
 */
public record SignupSession(String token, long inviteCodeId, int generation, String verifiedEmail,
        int emailSendCount, Instant expiresAt) {

    SignupSession withVerifiedEmail(String email) {
        return new SignupSession(token, inviteCodeId, generation, email, emailSendCount, expiresAt);
    }

    SignupSession withEmailSent() {
        return new SignupSession(token, inviteCodeId, generation, verifiedEmail, emailSendCount + 1, expiresAt);
    }

}
