package com.gdghost.ops;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "어드민")
@SecurityRequirement(name = "sessionCookie")
@RestController
public class AdminStaffController {

    private final StaffService staffService;

    public AdminStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @Operation(summary = "첫 운영진 지정", description = "운영진이 한 명도 없을 때 개발자가 지정한다. 이미 운영진이면 직함만 바꾼다.")
    @PutMapping("/api/v1/admin/members/{memberId}/staff")
    public StaffResponse assignStaff(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId,
            @RequestBody @Valid StaffAssignRequest request) {
        return staffService.assign(me.id(), memberId, request.staffTitle());
    }

}
