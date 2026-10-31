package com.gdghost.event;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "이벤트")
@RestController
@RequestMapping("/api/v1")
public class CertificationController {

    private final CertificationService certificationService;

    public CertificationController(CertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @Operation(summary = "인증 올리기", description = "Idempotency-Key 필수. 사진(purpose CERT로 받은 objectKey 1–3개) + 한 줄(1–100자). "
            + "확인 대기로 시작한다. 이벤트 기간 밖이면 409 EVENT_CLOSED, 사진이 R2에 없거나 남의 키면 400 UPLOAD_NOT_FOUND.")
    @Idempotent
    @PostMapping("/events/{eventId}/certifications")
    public ResponseEntity<CertificationItem> submit(@AuthenticationPrincipal AuthMember me,
            @PathVariable long eventId, @RequestBody @Valid CertificationCreateRequest request) {
        CertificationItem item = certificationService.submit(me, eventId, request);
        return ResponseEntity.created(URI.create("/api/v1/events/" + eventId + "/certifications")).body(item);
    }

    @Operation(summary = "반려된 인증 다시 올리기", description = "본인의 반려된 인증만, 이벤트 기간 안에만. 보낸 필드만 바꾸고 "
            + "photoKeys를 보내면 사진을 통째로 교체한다. 반려 사유 · 검토 기록을 비우고 다시 확인 대기로.")
    @PatchMapping("/certifications/{certificationId}")
    public CertificationItem resubmit(@AuthenticationPrincipal AuthMember me, @PathVariable long certificationId,
            @RequestBody @Valid CertificationUpdateRequest request) {
        return certificationService.resubmit(me, certificationId, request);
    }

}
