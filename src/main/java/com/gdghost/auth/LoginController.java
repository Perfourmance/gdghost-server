package com.gdghost.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.SessionCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @Operation(summary = "로그인", description = "remember true(기본)면 30일 쿠키, false면 브라우저를 닫으면 끝나는 쿠키. "
            + "실패 10분에 아이디당 5회 · IP당 50회 넘으면 LOGIN_LOCKED.")
    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@RequestBody @Valid LoginRequest request,
            @CookieValue(name = SessionCookie.NAME, required = false) String previousToken,
            HttpServletRequest servletRequest) {
        LoginResult result = loginService.login(request, servletRequest.getHeader(HttpHeaders.USER_AGENT),
                servletRequest.getRemoteAddr(), previousToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.of(result.loginToken(), result.remember()).toString())
                .body(result.me());
    }

}
