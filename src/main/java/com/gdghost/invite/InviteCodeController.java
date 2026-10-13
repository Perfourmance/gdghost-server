package com.gdghost.invite;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "운영 · 초대 코드")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/invite-codes")
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;

    public InviteCodeController(InviteCodeService inviteCodeService) {
        this.inviteCodeService = inviteCodeService;
    }

    @Operation(summary = "초대 코드 발급", description = "같은 기수의 쓸 수 있는 코드는 자동 폐기된다 (기수당 활성 1개).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteCodeResponse issue(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid InviteCodeIssueRequest request) {
        return inviteCodeService.issue(me.id(), request);
    }

    @Operation(summary = "초대 코드 목록", description = "최신순.")
    @GetMapping
    public OpsPage<InviteCodeResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return inviteCodeService.list(page, size);
    }

    @Operation(summary = "초대 코드 폐기", description = "이미 가입한 부원에는 영향 없음. 이미 폐기된 코드도 204.")
    @DeleteMapping("/{codeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthMember me, @PathVariable long codeId) {
        inviteCodeService.revoke(me.id(), codeId);
    }

    @Operation(summary = "코드별 가입자", description = "유출이 의심될 때 그 코드로 누가 가입했는지 확인.")
    @GetMapping("/{codeId}/members")
    public InviteCodeMembersResponse members(@PathVariable long codeId) {
        return new InviteCodeMembersResponse(inviteCodeService.members(codeId));
    }

}
