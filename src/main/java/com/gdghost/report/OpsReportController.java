package com.gdghost.report;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

@Tag(name = "운영 · 신고")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/reports")
public class OpsReportController {

    private final OpsReportService opsReportService;

    public OpsReportController(OpsReportService opsReportService) {
        this.opsReportService = opsReportService;
    }

    @Operation(summary = "신고 목록", description = "미처리(OPEN)가 위로, 그 안에서 최신순. state가 없으면 전체. "
            + "대상 작성자는 담지 않는다. targetPreview는 질문 제목 · 답변 · 공지 댓글 본문 앞 50자(지운 글도), "
            + "targetDeletedBy(null · AUTHOR · STAFF)로 되돌리기 가능 여부를 본다. sameTargetCount는 이 신고 포함.")
    @GetMapping
    public OpsPage<OpsReportResponse> list(@RequestParam(required = false) ReportState state,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return opsReportService.list(state, page, size);
    }

    @Operation(summary = "신고 처리", description = "DISMISS(괜찮음) · HIDE(글 숨김)는 OPEN에서만, "
            + "RESTORE(숨김 되돌리기)는 HIDDEN에서만 — 아니면 409. 같은 대상의 같은 상태 신고도 함께 닫는다. "
            + "작성자가 먼저 지운 글은 HIDE해도 그대로 두고 RESTORE는 409. 숨긴 답변이 채택이었으면 채택이 풀리고, "
            + "되돌려도 다시 걸지 않는다. 운영 기록에 남고 작성자에게 알리지 않는다. 응답은 목록 항목 하나.")
    @PatchMapping("/{reportId}")
    public OpsReportResponse handle(@AuthenticationPrincipal AuthMember me, @PathVariable long reportId,
            @RequestBody @Valid ReportHandleRequest request) {
        return opsReportService.handle(me.id(), reportId, request.action());
    }

}
