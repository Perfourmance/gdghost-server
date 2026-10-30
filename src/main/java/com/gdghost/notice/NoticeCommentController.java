package com.gdghost.notice;

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

@Tag(name = "공지")
@RestController
@RequestMapping("/api/v1")
public class NoticeCommentController {

    private final NoticeCommentService commentService;

    public NoticeCommentController(NoticeCommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "공지 댓글 작성", description = "Idempotency-Key 필수. 본문 1–500자(앞뒤 공백 뗌), 익명 없음. "
            + "부원당 분당 5회. 공지가 없거나 지워졌으면 404. 알림은 보내지 않는다. 응답은 공지 상세 comments 항목 모양.")
    @Idempotent
    @RateLimit(limit = 5)
    @PostMapping("/notices/{noticeId}/comments")
    public ResponseEntity<NoticeDetail.Comment> create(@AuthenticationPrincipal AuthMember me,
            @PathVariable long noticeId, @RequestBody @Valid NoticeCommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(me.id(), noticeId, request));
    }

    @Operation(summary = "공지 댓글 삭제", description = "본인 댓글만(운영진도 남의 댓글은 403 — 신고 처리의 숨김으로). "
            + "소프트 삭제, 상세에서 빠진다. 댓글이 없거나 지웠거나 공지가 지워졌으면 404.")
    @DeleteMapping("/notice-comments/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long commentId) {
        commentService.delete(me.id(), commentId);
        return ResponseEntity.noContent().build();
    }

}
