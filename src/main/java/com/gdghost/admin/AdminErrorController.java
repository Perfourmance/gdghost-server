package com.gdghost.admin;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Tag(name = "어드민")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/admin/errors")
public class AdminErrorController {

    private final ErrorLogReader reader;

    public AdminErrorController(ErrorLogReader reader) {
        this.reader = reader;
    }

    @Operation(summary = "에러 로그 목록", description = "같은 에러는 한 줄에 횟수로. 마지막 발생이 최근인 순. "
            + "q는 메시지 · 경로 검색, since는 이 시각 이후 발생한 것만(ISO-8601). 개발자가 아니면 404.")
    @GetMapping
    public CursorPage<ErrorLogResponse.Item> list(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Instant since, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return reader.list(q, since, cursor, size);
    }

    @Operation(summary = "에러 로그 상세", description = "목록 항목 + 스택. 이메일 · 비밀번호 · 토큰은 저장 전에 가린다.")
    @GetMapping("/{errorId}")
    public ErrorLogResponse.Detail detail(@PathVariable long errorId) {
        return reader.find(errorId);
    }

}
