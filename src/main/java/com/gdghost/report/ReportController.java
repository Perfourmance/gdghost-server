package com.gdghost.report;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "신고")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "신고하기", description = "Idempotency-Key 필수. 부원당 분당 5회. 사유 1–200자(앞뒤 공백 제외). "
            + "지금은 QUESTION · ANSWER · NOTICE_COMMENT만(나머지 400). 대상이 없거나 지워졌으면 404, 같은 대상을 이미 신고했으면 409. "
            + "작성자에게 알리지 않는다.")
    @Idempotent
    @RateLimit(limit = 5)
    @PostMapping
    public ResponseEntity<ReportCreatedResponse> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid ReportCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(me.id(), request));
    }

}
