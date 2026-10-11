package com.gdghost.common.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseCookie.ResponseCookieBuilder;

public final class SessionCookie {

    public static final String NAME = "GDG_SESSION";

    private SessionCookie() {
    }

    /** remember가 false면 Max-Age 없는 쿠키 — 브라우저를 닫으면 사라진다 */
    public static ResponseCookie of(String token, boolean remember) {
        ResponseCookieBuilder builder = base(token);
        if (remember) {
            builder.maxAge(LoginTokenService.LIFETIME);
        }
        return builder.build();
    }

    public static ResponseCookie expired() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/");
    }

}
