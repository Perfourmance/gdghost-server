package com.gdghost.admin;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "어드민")
@SecurityRequirement(name = "sessionCookie")
@RestController
public class AdminExportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RoutingExportService exportService;

    public AdminExportController(RoutingExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "라우팅 데이터 내보내기 (CSV)", description = "from · to는 한국 날짜, 필수, 최대 1년. 보낸 시각 기준. "
            + "이름 · 제목 · 본문 없이 id와 숫자만. 익명 질문은 member_id를 비운다. 요청마다 운영 기록 + 개발자 디스코드.")
    @GetMapping("/api/v1/admin/exports/routing.csv")
    public ResponseEntity<String> routing(@AuthenticationPrincipal AuthMember me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String filename = "routing_" + from + "_" + to + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(exportService.export(me.id(), from, to));
    }

}
