package com.gdghost.share;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

@Tag(name = "공유")
@RestController
@RequestMapping("/api/v1")
public class ShareNoteController {

    private final ShareNoteService noteService;

    public ShareNoteController(ShareNoteService noteService) {
        this.noteService = noteService;
    }

    @Operation(summary = "한 줄 덧붙이기", description = "Idempotency-Key 필수. 본문 1–100자(앞뒤 공백 뗌), 부원당 분당 5회. "
            + "글이 없거나 지워졌으면 404. 글쓴이에게 앱 내 알림 + 웹 푸시(본인이 단 건 없음). 응답은 공유 상세 notes 항목 모양.")
    @Idempotent
    @RateLimit(limit = 5)
    @PostMapping("/shares/{shareId}/notes")
    public ResponseEntity<ShareDetail.Note> create(@AuthenticationPrincipal AuthMember me,
            @PathVariable long shareId, @RequestBody @Valid ShareNoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(me.id(), shareId, request));
    }

    @Operation(summary = "한 줄 덧붙이기 삭제", description = "본인 것만(운영진도 남의 것은 403 — 신고 처리의 숨김으로). "
            + "소프트 삭제, 상세에서 빠진다. 없거나 지웠거나 글이 지워졌으면 404.")
    @DeleteMapping("/share-notes/{noteId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long noteId) {
        noteService.delete(me.id(), noteId);
        return ResponseEntity.noContent().build();
    }

}
