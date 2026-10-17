package com.gdghost.auth;

import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.LoginTokenService;
import com.gdghost.common.security.SessionCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "계정")
@RestController
@RequestMapping("/api/v1/me/email-change")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    public EmailChangeController(EmailChangeService emailChangeService) {
        this.emailChangeService = emailChangeService;
    }

    @Operation(summary = "이메일 변경 인증번호 발송 (본인)",
            description = "현재 비밀번호 확인 후 새 메일로 인증번호. 30일에 한 번. "
                    + "같은 메일 60초 · 하루 5회, 부원당 시간당 5회. 현재 비밀번호는 비밀번호 변경과 합쳐 10분에 5회 틀리면 429.")
    // 메일 주소를 바꿔 가며 서버 하루 메일 한도를 소진하지 못하게
    @RateLimit(limit = 5, unit = ChronoUnit.HOURS)
    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificationSentResponse sendCode(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid EmailChangeCodeRequest request) {
        return emailChangeService.sendCode(me.id(), request);
    }

    @Operation(summary = "이메일 변경 확인 (본인)",
            description = "번호가 맞으면 바꾼다. 지금 기기는 새 쿠키로 유지, 다른 기기는 모두 로그아웃. 이전 메일로 변경 안내.")
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@AuthenticationPrincipal AuthMember me,
            @CookieValue(name = SessionCookie.NAME) String currentToken,
            @RequestBody @Valid EmailChangeConfirmRequest request,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        LoginTokenService.Issued issued = emailChangeService.confirm(me.id(), currentToken, request, userAgent);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.of(issued.token(), issued.remember()).toString())
                .build();
    }

}
