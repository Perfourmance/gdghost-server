package com.gdghost.feed;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.idempotency.Idempotent;
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "피드")
@RestController
@RequestMapping("/api/v1")
public class FeedCommentController {

    private final FeedCommentService commentService;

    public FeedCommentController(FeedCommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "피드 댓글 목록", description = "댓글은 오래된 순(커서는 댓글 기준), 답글은 댓글 아래에 묶어서. "
            + "지운 댓글에 답글이 있으면 자리를 남기고 author · body 없이 deletedBy(AUTHOR · STAFF)만. 글이 없거나 지워졌으면 404.")
    @GetMapping("/feed-posts/{postId}/comments")
    public CursorPage<FeedCommentItem> list(@AuthenticationPrincipal AuthMember me, @PathVariable long postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return commentService.list(me.id(), postId, cursor, size);
    }

    @Operation(summary = "피드 댓글 · 답글 작성", description = "Idempotency-Key 필수. 본문 1–300자(앞뒤 공백 뗌), parentId가 있으면 답글. "
            + "답글에 답하면 부모 댓글에 붙이고 본문 앞에 @닉네임을 붙인다(합쳐서 300자 이하). 부원당 분당 5회. 글이나 parent가 없으면 404. "
            + "알림(앱 내 + 웹 푸시)은 댓글이면 글쓴이, 답글이면 대상 댓글 작성자. 자기 자신에게는 없다.")
    @Idempotent
    @RateLimit(limit = 5)
    @PostMapping("/feed-posts/{postId}/comments")
    public ResponseEntity<FeedCommentItem> create(@AuthenticationPrincipal AuthMember me, @PathVariable long postId,
            @RequestBody @Valid FeedCommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(me.id(), postId, request));
    }

    @Operation(summary = "피드 댓글 삭제", description = "본인 댓글만(운영진도 남의 댓글은 403 — 신고 처리의 숨김으로). 소프트 삭제. "
            + "없거나 지웠거나 글이 지워졌으면 404.")
    @DeleteMapping("/feed-comments/{commentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long commentId) {
        commentService.delete(me.id(), commentId);
        return ResponseEntity.noContent().build();
    }

}
