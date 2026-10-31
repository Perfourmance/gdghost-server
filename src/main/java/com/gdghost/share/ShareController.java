package com.gdghost.share;

import java.net.URI;
import java.time.temporal.ChronoUnit;

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
import com.gdghost.common.page.CursorPage;
import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "공유")
@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @Operation(summary = "공유 목록", description = "저장순이 기본, sort=LATEST면 최신순. 본문 · 사진 주소는 담지 않고 hasPhoto만. "
            + "비회원은 GuestShareSummary(제목까지), q를 쓰면 401. q는 제목 · 태그 검색 2–30자. 커서는 같은 정렬에서만 쓴다.")
    @GetMapping
    public CursorPage<?> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) ShareCategory category,
            @RequestParam(defaultValue = "SAVED") ShareSort sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return shareService.list(me, category, sort, q, cursor, size);
    }

    @Operation(summary = "공유 상세", description = "본문 · 사진(1시간 서명 주소) · 태그 · 한 줄 덧붙이기(오래된 순 전부). 없거나 삭제 · 숨김이면 404.")
    @GetMapping("/{shareId}")
    public ShareDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long shareId) {
        return shareService.detail(me.id(), shareId);
    }

    @Operation(summary = "공유 작성", description = "Idempotency-Key 필수. 태그는 공백을 떼고 중복을 뺀 뒤 최대 5개, 사진은 최대 3장(purpose SHARE). "
            + "남의 키 · 없는 파일은 400 UPLOAD_NOT_FOUND. 부원당 분당 3회 · 하루 30회(첫 작성부터 24시간).")
    @Idempotent
    @RateLimit(limit = 3)
    @RateLimit(limit = 30, unit = ChronoUnit.DAYS)
    @PostMapping
    public ResponseEntity<ShareDetail> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid ShareCreateRequest request) {
        ShareDetail detail = shareService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/shares/" + detail.id())).body(detail);
    }

    @Operation(summary = "공유 수정", description = "본인 글만. 보낸 필드만 바꾼다(null은 그대로). tags · photoKeys는 보내면 통째로 교체, 빈 배열은 전부 지운다. "
            + "빠진 사진은 정리 배치가 R2에서 지운다. 부원당 하루 20회(첫 수정부터 24시간).")
    @RateLimit(limit = 20, unit = ChronoUnit.DAYS)
    @PatchMapping("/{shareId}")
    public ShareDetail update(@AuthenticationPrincipal AuthMember me, @PathVariable long shareId,
            @RequestBody @Valid ShareUpdateRequest request) {
        return shareService.update(me.id(), shareId, request);
    }

    @Operation(summary = "공유 삭제", description = "본인 글만, 소프트 삭제. 사진은 연결을 끊어 정리 배치가 R2에서 지운다.")
    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthMember me, @PathVariable long shareId) {
        shareService.delete(me.id(), shareId);
        return ResponseEntity.noContent().build();
    }

}
