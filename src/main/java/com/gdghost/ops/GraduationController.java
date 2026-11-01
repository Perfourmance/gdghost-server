package com.gdghost.ops;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@Tag(name = "운영 · 부원")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/members")
public class GraduationController {

    private final GraduationService graduationService;

    public GraduationController(GraduationService graduationService) {
        this.graduationService = graduationService;
    }

    @Operation(summary = "졸업 처리 대상",
            description = "활동 중인 재학 · 휴학 부원. studentNoMax가 21이면 21학번 이전이고 학번이 없는 사람은 빠진다.")
    @GetMapping("/graduation-candidates")
    public GraduationCandidatesResponse candidates(@RequestParam(required = false) Integer generation,
            @RequestParam(required = false) @Pattern(regexp = "\\d{2}") String studentNoMax) {
        return graduationService.candidates(generation, studentNoMax);
    }

    @Operation(summary = "졸업 일괄 처리",
            description = "1–200명. 이미 졸업 · 탈퇴 · 정지이거나 없는 부원은 skippedIds로 돌려준다. 바뀐 부원에게 앱 내 알림.")
    @PostMapping("/graduations")
    public GraduationResponse graduate(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid GraduationRequest request) {
        return graduationService.graduate(me.id(), request.memberIds());
    }

}
