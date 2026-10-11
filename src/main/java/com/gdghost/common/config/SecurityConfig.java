package com.gdghost.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.security.LoginTokenAuthenticationFilter;
import com.gdghost.common.security.LoginTokenService;

/**
 * 쿠키 인증(GDG_SESSION) · CSRF(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더). 권한 규칙은 M1에서 채운다.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/actuator/health",
    };

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
                .addFilterBefore(new LoginTokenAuthenticationFilter(tokens), AnonymousAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, e) -> resolver.resolveException(
                                request, response, null, new BusinessException(ErrorCode.UNAUTHENTICATED)))
                        .accessDeniedHandler((request, response, e) -> resolver.resolveException(
                                request, response, null, new BusinessException(
                                        e instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
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
