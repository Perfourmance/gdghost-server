package com.gdghost.member;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.SessionCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "계정")
@RestController
@RequestMapping("/api/v1/me")
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;

    public PasswordChangeController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    @Operation(summary = "비밀번호 변경",
            description = "지금 기기는 새 쿠키로 유지, 다른 기기는 모두 로그아웃. 현재 비밀번호 10분에 5회 틀리면 429.")
    @PutMapping("/password")
    public ResponseEntity<Void> change(@AuthenticationPrincipal AuthMember me,
            @CookieValue(name = SessionCookie.NAME) String currentToken,
            @RequestBody @Valid PasswordChangeRequest request,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        LoginTokenService.Issued issued = passwordChangeService.change(me.id(), currentToken, request, userAgent);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.of(issued.token(), issued.remember()).toString())
                .build();
    }

}
