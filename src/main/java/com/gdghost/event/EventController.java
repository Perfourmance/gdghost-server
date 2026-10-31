package com.gdghost.event;

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

@Tag(name = "이벤트")
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "이벤트 목록", description = "진행 중 → 예정 → 지난 순, 페이지 없이 전부. state는 오늘(한국 날짜)로 계산한다.")
    @GetMapping
    public EventListResponse list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) EventState state) {
        return eventService.list(me.id(), state);
    }

    @Operation(summary = "이벤트 상세", description = "설명 · 4단계 안내 · 내 인증 현황 · 참여 순위(인증 횟수 상위 10명, 같은 횟수는 같은 순위). "
            + "pendingCount는 운영진에게만, 아니면 null.")
    @GetMapping("/{eventId}")
    public EventDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long eventId) {
        return eventService.detail(me, eventId);
    }

    @Operation(summary = "인증 글 목록", description = "최신순. 부원에게는 인증(APPROVED)된 글과 내 글만, 운영진은 전부. "
            + "rejectReason은 작성자 본인과 운영진에게만. 사진 url은 이 응답을 받을 권한이 있을 때만 발급된다.")
    @GetMapping("/{eventId}/certifications")
    public CursorPage<CertificationItem> certifications(@AuthenticationPrincipal AuthMember me,
            @PathVariable long eventId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return eventService.certifications(me, eventId, cursor, size);
    }

}
