package com.gdghost.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;
import com.gdghost.embedding.AiLogWriter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Tag(name = "어드민")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminLogController {

    private final AdminLogReader reader;

    public AdminLogController(AdminLogReader reader) {
        this.reader = reader;
    }

    @Operation(summary = "배치 로그", description = "최신순. summary.last24hFailed는 필터와 상관없이 최근 24시간 실패 수. "
            + "없는 jobName은 빈 목록. 개발자가 아니면 404.")
    @GetMapping("/batches")
    public AdminLogResponse.Batches batches(@RequestParam(required = false) @Size(max = 50) String jobName,
            @RequestParam(required = false) BatchLogState state, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return reader.batches(jobName, state, cursor, size);
    }

    @Operation(summary = "AI 로그", description = "최신순. summary.last7d는 필터와 상관없이 최근 7일, "
            + "p95LatencyMs는 성공한 호출 기준이고 없으면 null. 개발자가 아니면 404.")
    @GetMapping("/ai-logs")
    public AdminLogResponse.AiLogs aiLogs(@RequestParam(required = false) AiLogWriter.Kind kind,
            @RequestParam(required = false) AiLogWriter.State state, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return reader.aiLogs(kind, state, cursor, size);
    }

}
