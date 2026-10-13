package com.gdghost.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

import com.gdghost.common.security.TokenHasher;

/**
 * 초대 코드 실패를 기기별로 세기 위한 쿠키. 같은 와이파이(같은 IP)의 신입 부원끼리 함께 잠기지 않게 한다.
 * 쿠키를 버리면 피할 수 있으므로 IP 기준 상한을 함께 둔다.
 */
final class SignupDeviceCookie {

    static final String NAME = "GDG_DEVICE";

    private SignupDeviceCookie() {
    }

    static String newId() {
        return TokenHasher.newToken();
    }

    /** 잠금 키로 쓰므로 길이가 이상한 값은 버린다 */
    static boolean isValid(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{43}");
    }

    static ResponseCookie of(String id) {
        return ResponseCookie.from(NAME, id)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/auth/signup")
                .maxAge(Duration.ofDays(1))
                .build();
    }

}
