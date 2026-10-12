package com.gdghost.common.security;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 보안 필터 체인 맨 앞. 뒤쪽 필터에서 난 예상 못 한 예외(로그인 토큰 확인 중 DB 장애 등)를
 * 컨트롤러 예외처럼 GlobalExceptionHandler로 넘겨 500 Problem + error_log로 만든다.
 * 그냥 두면 톰캣이 /error로 다시 보내는데, 그때는 로그인 필터를 건너뛰어 401로 바뀐다.
 * 401 · 403 거부와 컨트롤러 예외는 앞에서 이미 처리돼 여기까지 오지 않는다.
 */
public class FilterExceptionFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver resolver;

    public FilterExceptionFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            // 응답이 이미 나가기 시작했으면 바꿀 수 없음
            if (response.isCommitted()) {
                throw e;
            }
            resolver.resolveException(request, response, null, e);
        }
    }

}
