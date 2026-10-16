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
@RequestMapping("/api/v1/auth")
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;

    public AccountRecoveryController(AccountRecoveryService accountRecoveryService) {
        this.accountRecoveryService = accountRecoveryService;
    }

    @Operation(summary = "아이디 찾기",
            description = "가입한 메일로 아이디를 보낸다. 가입 안 된 메일에도 같은 응답. "
                    + "재발송 대기 60초 · 같은 메일 하루 5회는 비밀번호 재설정 인증번호와 합산, IP당 시간당 20회.")
    // 가입된 메일을 바꿔 가며 서버 하루 메일 한도를 소진하지 못하게
    @RateLimit(limit = 20, unit = ChronoUnit.HOURS, per = RateLimit.Per.IP)
    @PostMapping("/find-login-id")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void findLoginId(@RequestBody @Valid FindLoginIdRequest request) {
        accountRecoveryService.findLoginId(request.email());
    }

}
