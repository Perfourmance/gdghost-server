package com.gdghost.common.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * GDG_SESSION 쿠키로 로그인 상태를 만든다. 쓸 수 없는 토큰이면 쿠키를 지우고 비회원으로 넘긴다
 * — 로그인이 필요한 API는 뒤에서 401.
 */
public class LoginTokenAuthenticationFilter extends OncePerRequestFilter {

    private final LoginTokenService tokens;

    public LoginTokenAuthenticationFilter(LoginTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, SessionCookie.NAME);
        if (cookie != null && !cookie.getValue().isEmpty()) {
            String token = cookie.getValue();
            tokens.authenticate(token).ifPresentOrElse(session -> {
                login(session.member());
                if (session.extended() && session.remember()) {
                    response.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.of(token, true).toString());
                }
            }, () -> response.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.expired().toString()));
        }
        chain.doFilter(request, response);
    }

    private static void login(AuthMember member) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(member, null, member.authorities()));
        SecurityContextHolder.setContext(context);
    }

}
