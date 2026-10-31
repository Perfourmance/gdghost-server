package com.gdghost.feed;

import java.net.URI;
import java.time.temporal.ChronoUnit;

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
@RequestMapping("/api/v1/feed-posts")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @Operation(summary = "피드 목록", description = "최신순, 삭제 · 숨김 제외. 사진은 1시간 서명 주소. 댓글은 담지 않고 commentCount만.")
    @GetMapping
    public CursorPage<FeedPostItem> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) FeedCategory category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return feedService.list(me.id(), category, cursor, size);
    }

    @Operation(summary = "피드 상세", description = "목록 항목과 같은 모양. 댓글은 피드 댓글 목록으로 따로 부른다. 없거나 삭제 · 숨김이면 404.")
    @GetMapping("/{postId}")
    public FeedPostItem detail(@AuthenticationPrincipal AuthMember me, @PathVariable long postId) {
        return feedService.detail(me.id(), postId);
    }

    @Operation(summary = "피드 글쓰기", description = "Idempotency-Key 필수. 본문 1–1000자(앞뒤 공백 뗌), 사진 최대 3장(purpose FEED). "
            + "남의 키 · 없는 파일은 400 UPLOAD_NOT_FOUND. 수정은 없다. 부원당 분당 3회 · 하루 30회(첫 작성부터 24시간).")
    @Idempotent
    @RateLimit(limit = 3)
    @RateLimit(limit = 30, unit = ChronoUnit.DAYS)
    @PostMapping
    public ResponseEntity<FeedPostItem> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid FeedPostCreateRequest request) {
        FeedPostItem item = feedService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/feed-posts/" + item.id())).body(item);
    }

    @Operation(summary = "피드 삭제", description = "본인 글만, 소프트 삭제. 사진은 연결을 끊어 정리 배치가 R2에서 지운다.")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long postId) {
        feedService.delete(me.id(), postId);
        return ResponseEntity.noContent().build();
    }

}
