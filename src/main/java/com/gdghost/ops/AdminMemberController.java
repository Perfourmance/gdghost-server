package com.gdghost.ops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.OpsPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 비상용 운영진 지정 · 이메일 변경 요청의 대상 고르기 — 전체 검색은 숨긴 · 정지된 부원을 빼므로 따로 둔다 */
@Tag(name = "어드민")
@SecurityRequirement(name = "sessionCookie")
@RestController
public class AdminMemberController {

    private final OpsMemberService opsMemberService;

    public AdminMemberController(OpsMemberService opsMemberService) {
        this.opsMemberService = opsMemberService;
    }

    @Operation(summary = "어드민 부원 목록",
            description = "최근 가입순. 목록 숨김을 켠 사람 · 정지된 사람도 보이고 탈퇴자는 빠진다. 이메일은 담지 않는다. "
                    + "q는 이름 · 닉네임 · 아이디 부분 일치.")
    @GetMapping("/api/v1/admin/members")
    public OpsPage<OpsMemberResponse> list(@RequestParam(required = false) @Size(max = 50) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return opsMemberService.list(q, null, null, null, null, page, size);
    }

}
