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
import com.gdghost.notice.NoticeDetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "운영 · 이벤트")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/events")
public class OpsEventController {

    private final OpsEventService opsEventService;

    public OpsEventController(OpsEventService opsEventService) {
        this.opsEventService = opsEventService;
    }

    @Operation(summary = "이벤트 생성", description = "Idempotency-Key 필수. 4단계 안내는 정확히 4개. 만든다고 알림이 가지 않는다. "
            + "응답은 이벤트 상세 모양.")
    @Idempotent
    @PostMapping
    public ResponseEntity<EventDetail> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid EventCreateRequest request) {
        EventDetail event = opsEventService.create(me, request);
        return ResponseEntity.created(URI.create("/api/v1/events/" + event.id())).body(event);
    }

    @Operation(summary = "이벤트 수정", description = "보낸 필드만 바꾼다. 삭제는 없고 기간을 끝내면 종료된다.")
    @PatchMapping("/{eventId}")
    public EventDetail update(@AuthenticationPrincipal AuthMember me, @PathVariable long eventId,
            @RequestBody @Valid EventUpdateRequest request) {
        return opsEventService.update(me, eventId, request);
    }

    @Operation(summary = "이벤트 안내를 공지로 발행", description = "Idempotency-Key 필수. 설명 · 4단계 안내로 공지를 만든다. "
            + "isPinned 기본 true · isMustRead 기본 false, 마감일은 이벤트 종료일. 이후는 공지 작성과 같다(디스코드 · 알림). "
            + "새 키로 여러 번 발행할 수 있다. 끝난 이벤트는 마감일이 지나 400. 응답은 공지 상세 모양.")
    @Idempotent
    @PostMapping("/{eventId}/notice")
    public ResponseEntity<NoticeDetail> publishNotice(@AuthenticationPrincipal AuthMember me,
            @PathVariable long eventId, @RequestBody(required = false) EventNoticeRequest request) {
        NoticeDetail notice = opsEventService.publishNotice(me, eventId, request);
        return ResponseEntity.created(URI.create("/api/v1/notices/" + notice.id())).body(notice);
    }

}
