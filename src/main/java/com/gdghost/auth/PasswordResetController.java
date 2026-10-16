package com.gdghost.auth;

import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "비밀번호 재설정 인증번호 발송",
            description = "가입 안 된 메일에도 같은 응답. 60초 안 재발송 불가, 같은 메일 하루 5회, IP당 시간당 20회.")
    // 가입된 메일을 바꿔 가며 서버 하루 메일 한도를 소진하지 못하게
    @RateLimit(limit = 20, unit = ChronoUnit.HOURS, per = RateLimit.Per.IP)
    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetCodeSentResponse sendCode(@RequestBody @Valid PasswordResetCodeRequest request) {
        return passwordResetService.sendCode(request.email());
    }

    @Operation(summary = "비밀번호 재설정 인증번호 확인", description = "맞으면 10분짜리 resetToken. 5회 틀리면 번호가 무효가 된다.")
    @PostMapping("/verify")
    public PasswordResetTokenResponse verify(@RequestBody @Valid PasswordResetVerifyRequest request) {
        return passwordResetService.verify(request.email(), request.code());
    }

    @Operation(summary = "새 비밀번호 설정", description = "모든 기기를 로그아웃한다. 자동 로그인은 하지 않음.")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody @Valid PasswordResetRequest request) {
        passwordResetService.reset(request.resetToken(), request.newPassword());
    }

}
