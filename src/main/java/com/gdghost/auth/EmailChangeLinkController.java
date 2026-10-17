package com.gdghost.auth;

import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "인증")
@RestController
public class EmailChangeLinkController {

    private final StaffEmailChangeService staffEmailChangeService;

    public EmailChangeLinkController(StaffEmailChangeService staffEmailChangeService) {
        this.staffEmailChangeService = staffEmailChangeService;
    }

    @Operation(summary = "운영진 이메일 변경 확인 (링크)",
            description = "메일 링크의 토큰으로 반영. 한 번만 · 가장 최근 링크만 유효. 모든 기기 로그아웃, 이전 메일로 안내. "
                    + "비밀번호는 그대로 — 새 메일로 재설정하게 안내.")
    @RateLimit(limit = 20, unit = ChronoUnit.HOURS, per = RateLimit.Per.IP)
    @PostMapping("/api/v1/auth/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestBody @Valid EmailChangeLinkConfirmRequest request) {
        staffEmailChangeService.confirm(request.token());
    }

}
