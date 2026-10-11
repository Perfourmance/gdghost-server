package com.gdghost.support;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * 프론트처럼 XSRF-TOKEN 쿠키와 같은 값을 X-XSRF-TOKEN 헤더로 보낸다.
 * spring-security-test의 csrf()는 공유 컨텍스트의 토큰 저장소를 세션 방식으로 바꿔 버려
 * 이후 테스트에서 XSRF-TOKEN 쿠키가 안 나오므로 쓰지 않는다.
 */
public final class TestCsrf {

    public static final String COOKIE = "XSRF-TOKEN";
    public static final String HEADER = "X-XSRF-TOKEN";
    private static final String TOKEN = "test-csrf-token";

    private TestCsrf() {
    }

    public static RequestPostProcessor csrfToken() {
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            request.setCookies(Stream.concat(Arrays.stream(existing), Stream.of(new Cookie(COOKIE, TOKEN)))
                    .toArray(Cookie[]::new));
            request.addHeader(HEADER, TOKEN);
            return request;
        };
    }

}
