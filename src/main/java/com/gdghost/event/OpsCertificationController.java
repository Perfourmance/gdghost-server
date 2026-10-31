package com.gdghost.event;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "운영 · 이벤트")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/certifications")
public class OpsCertificationController {

    private final OpsCertificationService service;

    public OpsCertificationController(OpsCertificationService service) {
        this.service = service;
    }

    @Operation(summary = "인증 확인 대기 목록", description = "여러 이벤트를 한 곳에서, 오래된 순(다시 올린 글은 다시 올린 시각). "
            + "state 기본 PENDING, eventId로 한 이벤트만. 탈퇴한 부원의 대기 인증은 빠진다.")
    @GetMapping
    public OpsPage<OpsCertificationItem> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(defaultValue = "PENDING") CertificationState state,
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(me, state, eventId, page, size);
    }

    @Operation(summary = "인증 승인 · 반려", description = "decision APPROVE · REJECT. 반려는 rejectReason(2–100자) 필수. "
            + "이미 검토됐으면 409. 작성자에게 앱 내 알림 + 웹 푸시, 운영 기록에 남는다.")
    @PostMapping("/{certificationId}/review")
    public CertificationItem review(@AuthenticationPrincipal AuthMember me, @PathVariable long certificationId,
            @RequestBody @Valid CertificationReviewRequest request) {
        return service.review(me, certificationId, request);
    }

}
