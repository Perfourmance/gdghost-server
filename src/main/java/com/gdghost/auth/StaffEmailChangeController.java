package com.gdghost.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "어드민")
@SecurityRequirement(name = "sessionCookie")
@RestController
public class StaffEmailChangeController {

    private final StaffEmailChangeService staffEmailChangeService;

    public StaffEmailChangeController(StaffEmailChangeService staffEmailChangeService) {
        this.staffEmailChangeService = staffEmailChangeService;
    }

    @Operation(summary = "운영진 이메일 변경 요청",
            description = "개발자 전용. 로그인도 메일도 안 되는 부원만. 개발자 본인 비밀번호 확인 후 새 메일로 확인 링크(24시간), "
                    + "이전 메일로 요청 알림. 부원이 링크를 열어야 반영된다. 자기 자신은 403(본인 변경 사용). "
                    + "현재 비밀번호는 비밀번호 변경 · 이메일 변경과 합쳐 10분에 5회 틀리면 429.")
    @PostMapping("/api/v1/admin/members/{memberId}/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailChangeLinkSentResponse request(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId,
            @RequestBody @Valid StaffEmailChangeRequest request) {
        return staffEmailChangeService.request(me.id(), memberId, request);
    }

}
