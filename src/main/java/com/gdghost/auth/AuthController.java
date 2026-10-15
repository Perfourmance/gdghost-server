package com.gdghost.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.SessionCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginService loginService;
    private final LoginTokenService loginTokens;
    private final MeService meService;

    public AuthController(LoginService loginService, LoginTokenService loginTokens, MeService meService) {
        this.loginService = loginService;
        this.loginTokens = loginTokens;
        this.meService = meService;
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

    @Operation(summary = "로그아웃", description = "이 기기의 로그인만 끝낸다. 다른 기기는 그대로.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = SessionCookie.NAME) String token) {
        loginTokens.revoke(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.expired().toString())
                .build();
    }

    @Operation(summary = "내 세션 확인", description = "앱을 열 때 가장 먼저 부른다. 401이면 비회원 화면으로.")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthMember me) {
        return meService.me(me.id());
    }

}
