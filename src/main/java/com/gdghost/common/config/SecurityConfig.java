package com.gdghost.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.FilterExceptionFilter;
import com.gdghost.common.security.LoginTokenAuthenticationFilter;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.Role;

/**
 * 쿠키 인증(GDG_SESSION) · CSRF(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더) · 권한.
 * 권한은 API 설계서(노션) API 목록의 '권한' · '비회원 처리'를 따른다 — 테스트 표 api-permissions.csv.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/actuator/health",
            "/error",   // 막아 두면 톰캣이 넘긴 에러가 401로 바뀜
    };

    // 비회원 처리 '허용' · '제목만'. 그 밖의 /api/v1은 로그인 필요
    private static final String[] GUEST_GET_APIS = {
            "/api/v1/auth/login-ids/availability", "/api/v1/auth/nicknames/availability",
            "/api/v1/tags", "/api/v1/home", "/api/v1/questions", "/api/v1/schedules", "/api/v1/shares",
    };
    private static final String[] GUEST_POST_APIS = {
            "/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/signup/invite-code",
            "/api/v1/auth/signup/email-verifications", "/api/v1/auth/signup/email-verifications/confirm",
            "/api/v1/auth/find-login-id", "/api/v1/auth/email-change/confirm",
            "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/codes", "/api/v1/auth/password-reset/verify",
    };

    // 어드민 API는 있는지조차 숨긴다 — 개발자가 아니면 비회원이어도 404
    private static final RequestMatcher ADMIN_APIS = PathPatternRequestMatcher.pathPattern("/api/v1/admin/**");

    /**
     * 보안 필터의 거부도 GlobalExceptionHandler를 거쳐 같은 Problem 모양으로 나간다.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginTokenService tokens,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        return http
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new FilterExceptionFilter(resolver), DisableEncodeUrlFilter.class)
                .addFilterBefore(new LoginTokenAuthenticationFilter(tokens), AnonymousAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> resolver.resolveException(
                                request, response, null, new BusinessException(
                                        ADMIN_APIS.matches(request) ? ErrorCode.NOT_FOUND : ErrorCode.UNAUTHENTICATED)))
                        .accessDeniedHandler((request, response, e) -> resolver.resolveException(
                                request, response, null, new BusinessException(
                                        e instanceof CsrfException ? ErrorCode.CSRF_INVALID
                                                : ADMIN_APIS.matches(request) ? ErrorCode.NOT_FOUND
                                                : ErrorCode.FORBIDDEN))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, GUEST_GET_APIS).permitAll()
                        .requestMatchers(HttpMethod.POST, GUEST_POST_APIS).permitAll()
                        .requestMatchers("/api/v1/ops/**").hasRole(Role.STAFF.name())
                        .requestMatchers(ADMIN_APIS).hasRole(Role.DEV.name())
                        .anyRequest().authenticated())
                .build();
    }

    /**
     * 프론트 JS가 읽어 헤더로 보내야 하므로 HttpOnly가 아니다.
     * spa()는 쿠키가 없는 요청마다 새로 발급 — 앱이 처음 부르는 /auth/me(비회원 401 포함)에서 첫 발급된다.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax").path("/"));
        return repository;
    }

}
