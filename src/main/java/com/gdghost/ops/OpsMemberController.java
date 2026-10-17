package com.gdghost.ops;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.Role;
import com.gdghost.member.MemberStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Tag(name = "운영 · 부원")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/members")
public class OpsMemberController {

    private final OpsMemberService opsMemberService;

    public OpsMemberController(OpsMemberService opsMemberService) {
        this.opsMemberService = opsMemberService;
    }

    @Operation(summary = "운영 부원 목록",
            description = "최근 가입순. 목록 숨김을 켠 사람도 보이고 탈퇴자는 빠진다. 이메일은 담지 않는다. "
                    + "q는 이름 · 닉네임 · 아이디 부분 일치.")
    @GetMapping
    public OpsPage<OpsMemberResponse> list(@RequestParam(required = false) @Size(max = 50) String q,
            @RequestParam(required = false) Integer generation,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean suspended,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return opsMemberService.list(q, generation, status, role, suspended, page, size);
    }

    @Operation(summary = "가입 이메일 보기", description = "본인 확인한 뒤에만. 부를 때마다 열람 기록(EMAIL_VIEW)이 남는다.")
    @GetMapping("/{memberId}/email")
    public ResponseEntity<MemberEmailResponse> email(@AuthenticationPrincipal AuthMember me,
            @PathVariable long memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(opsMemberService.viewEmail(me.id(), memberId));
    }

    @Operation(summary = "부원별 운영 기록", description = "그 부원을 대상으로 한 운영 행동, 최신순. 이 조회는 기록하지 않는다.")
    @GetMapping("/{memberId}/audit-logs")
    public OpsPage<OpsAuditLogResponse> auditLogs(@PathVariable long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return opsMemberService.auditLogs(memberId, page, size);
    }

}
