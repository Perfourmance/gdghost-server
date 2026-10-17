package com.gdghost.ops;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "운영 · 부원")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/members/{memberId}/suspension")
public class SuspensionController {

    private final SuspensionService suspensionService;

    public SuspensionController(SuspensionService suspensionService) {
        this.suspensionService = suspensionService;
    }

    @Operation(summary = "계정 정지",
            description = "즉시 로그아웃되고 다시 로그인할 수 없다. 작성한 글은 그대로. 본인은 400, 운영진 · 개발자는 409(먼저 운영진 해제). "
                    + "이미 정지된 부원이면 지금 상태를 그대로 돌려준다.")
    @PostMapping
    public SuspensionResponse suspend(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId,
            @RequestBody @Valid SuspendRequest request) {
        return suspensionService.suspend(me.id(), memberId, request.reason());
    }

    @Operation(summary = "계정 정지 해제", description = "정지되지 않은 부원이면 404. 부원은 다시 로그인해야 한다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsuspend(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId) {
        suspensionService.unsuspend(me.id(), memberId);
    }

}
