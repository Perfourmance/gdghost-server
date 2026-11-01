package com.gdghost.ops;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
@RequestMapping("/api/v1/ops/members/{memberId}/staff")
public class OpsStaffController {

    private final StaffService staffService;

    public OpsStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @Operation(summary = "운영진 지정",
            description = "추가 지정이다. 이미 운영진이면 직함만 바꾼다. 탈퇴 · 정지 · 개발자 계정은 400.")
    @PutMapping
    public StaffResponse assign(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId,
            @RequestBody @Valid StaffAssignRequest request) {
        return staffService.assign(me.id(), memberId, request.staffTitle());
    }

    @Operation(summary = "운영진 해제",
            description = "본인 해제도 된다. 운영진이 아닌 부원은 404, 마지막 운영진은 409. 다음 요청부터 운영 API가 403.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId) {
        staffService.revoke(me.id(), memberId);
    }

}
