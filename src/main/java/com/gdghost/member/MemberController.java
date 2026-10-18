package com.gdghost.member;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "부원")
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "부원 목록", description = "목록 숨김 · 탈퇴 · 정지 계정은 빠진다. 기수 내림차순 → 닉네임. "
            + "answerCount는 익명 · 답글 제외.")
    @GetMapping
    public CursorPage<MemberSummary> list(@RequestParam(required = false) Integer generation,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return memberService.list(generation, status, cursor, size);
    }

    @Operation(summary = "프로필 상세", description = "목록 숨김이어도 직접 열면 보인다. 연락 수단은 공개 항목만(본인은 전부). "
            + "없거나 탈퇴 · 정지한 부원은 404.")
    @GetMapping("/{memberId}")
    public MemberProfileView profile(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId) {
        return memberService.profile(me.id(), memberId);
    }

    @Operation(summary = "프로필 활동 기록", description = "type 필수. 익명 글은 남이 볼 때 빠지고, 본인 조회일 때만 "
            + "anonymous와 함께 포함. SHARE · FEED는 9단계부터.")
    @GetMapping("/{memberId}/activities")
    public CursorPage<ActivityItem> activities(@AuthenticationPrincipal AuthMember me, @PathVariable long memberId,
            @RequestParam ActivityType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return memberService.activities(me.id(), memberId, type, cursor, size);
    }

}
