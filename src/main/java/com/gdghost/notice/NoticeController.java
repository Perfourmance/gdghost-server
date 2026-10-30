package com.gdghost.notice;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "공지")
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "공지 목록", description = "고정 먼저, 그 안에서 최신순. 지운 공지 제외, 본문은 담지 않는다. "
            + "read = 열어 봄, confirmed = 확인했어요 누름 (보는 사람 기준). commentCount는 지운 댓글 제외.")
    @GetMapping
    public CursorPage<NoticeSummary> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return noticeService.list(me.id(), cursor, size);
    }

    @Operation(summary = "공지 상세", description = "열면 읽음으로 남긴다(처음 시각 유지). "
            + "stats는 운영진 · 개발자에게만, 부원은 null. 댓글은 지운 것 빼고 오래된 순 전부. 없거나 지웠으면 404.")
    @GetMapping("/{noticeId}")
    public NoticeDetail detail(@AuthenticationPrincipal AuthMember me, @PathVariable long noticeId) {
        return noticeService.detail(me, noticeId);
    }

    @Operation(summary = "공지 확인했어요", description = "본문 없음. 한 번만 집계되고 다시 눌러도 처음 시각 그대로(멱등). "
            + "읽지 않았으면 읽음도 함께. 취소는 없다. 없거나 지웠으면 404.")
    @PutMapping("/{noticeId}/confirmation")
    public NoticeConfirmation confirm(@AuthenticationPrincipal AuthMember me, @PathVariable long noticeId) {
        return noticeService.confirm(me.id(), noticeId);
    }

}
