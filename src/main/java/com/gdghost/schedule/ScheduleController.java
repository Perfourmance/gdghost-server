package com.gdghost.schedule;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "일정")
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "일정 목록 (캘린더)", description = "from · to 필수(한국 날짜), 최대 62일. 기간이 걸치는 일정 모두, 페이지 없음. "
            + "비회원은 GuestScheduleSummary(제목 · 날짜까지). dDay는 마감(종료일, 없으면 시작일)까지 남은 날, 지나면 음수. "
            + "deadlineSoon은 마감까지 0–3일.")
    @GetMapping
    public ScheduleListResponse list(@AuthenticationPrincipal AuthMember me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ScheduleKind kind) {
        return scheduleService.list(me, from, to, kind);
    }

    @Operation(summary = "일정 상세", description = "메모와 연결된 모집까지. 없거나 지웠으면 404.")
    @GetMapping("/{scheduleId}")
    public ScheduleDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long scheduleId) {
        return scheduleService.detail(me.id(), scheduleId);
    }

    @Operation(summary = "일정 등록", description = "Idempotency-Key 필수. 공모전 · 채용은 부원 누구나, 동아리 · 학사는 운영진만(공식 일정).")
    @Idempotent
    @PostMapping
    public ResponseEntity<ScheduleDetail> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid ScheduleCreateRequest request) {
        ScheduleDetail detail = scheduleService.create(me, request);
        return ResponseEntity.created(URI.create("/api/v1/schedules/" + detail.id())).body(detail);
    }

    @Operation(summary = "일정 수정", description = "작성자 또는 운영진. 보낸 필드만 바꾼다. 장소 · 메모는 빈 문자열이면 지우고, "
            + "종료일은 clearEndDate로 지운다. 동아리 · 학사로 바꾸는 것은 운영진만.")
    @PatchMapping("/{scheduleId}")
    public ScheduleDetail update(@AuthenticationPrincipal AuthMember me, @PathVariable long scheduleId,
            @RequestBody @Valid ScheduleUpdateRequest request) {
        return scheduleService.update(me, scheduleId, request);
    }

    @Operation(summary = "일정 삭제", description = "작성자 또는 운영진. 연결된 모집은 남기고 연결만 끊는다.")
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long scheduleId) {
        scheduleService.delete(me, scheduleId);
        return ResponseEntity.noContent().build();
    }

}
