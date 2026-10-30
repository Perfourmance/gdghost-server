package com.gdghost.notice;

import java.net.URI;

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
import com.gdghost.common.page.OpsPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "운영 · 공지")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1/ops/notices")
public class OpsNoticeController {

    private final OpsNoticeService opsNoticeService;
    private final NoticeReminderService reminderService;

    public OpsNoticeController(OpsNoticeService opsNoticeService, NoticeReminderService reminderService) {
        this.opsNoticeService = opsNoticeService;
        this.reminderService = reminderService;
    }

    @Operation(summary = "공지 작성", description = "Idempotency-Key 필수. isPinned 기본 true · isMustRead 기본 false, "
            + "dueDate는 오늘(한국 날짜)부터, 없으면 상시. 작성자는 바로 확인함. "
            + "커밋 후 부원 디스코드 채널로 제목 · 링크(필독이면 [필독]). 응답은 공지 상세 모양.")
    @Idempotent
    @PostMapping
    public ResponseEntity<NoticeDetail> create(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid NoticeCreateRequest request) {
        NoticeDetail notice = opsNoticeService.create(me, request);
        return ResponseEntity.created(URI.create("/api/v1/notices/" + notice.id())).body(notice);
    }

    @Operation(summary = "미확인 공지 재알림", description = "확인했어요를 누르지 않은 활동 중 부원에게 앱 내 알림 + 웹 푸시. "
            + "공지당 1회(이미 보냈으면 409), 지운 공지는 404. 22–08시(KST)면 푸시만 08시로 미룬다. 디스코드는 쓰지 않는다.")
    @PostMapping("/{noticeId}/reminders")
    public ResponseEntity<NoticeReminderResponse> remind(@PathVariable long noticeId) {
        return ResponseEntity.accepted().body(reminderService.remind(noticeId));
    }

    @Operation(summary = "운영 공지 목록", description = "최신순, 지운 공지 제외. 본문은 담지 않는다. "
            + "stats는 탈퇴 · 정지하지 않은 사람 기준(N명 중 M명 확인). commentCount는 지운 댓글 제외.")
    @GetMapping
    public OpsPage<OpsNoticeResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return opsNoticeService.list(page, size);
    }

    @Operation(summary = "공지 수정", description = "다른 운영진이 쓴 공지도. 보낸 필드만 바꾼다(null은 그대로). "
            + "마감일을 지우려면 clearDueDate=true (dueDate와 함께 보내면 400). 디스코드에 다시 올리지 않는다.")
    @PatchMapping("/{noticeId}")
    public NoticeDetail update(@AuthenticationPrincipal AuthMember me, @PathVariable long noticeId,
            @RequestBody @Valid NoticeUpdateRequest request) {
        return opsNoticeService.update(me, noticeId, request);
    }

    @Operation(summary = "공지 삭제", description = "소프트 삭제. 이미 디스코드에 올라간 메시지는 지우지 않는다.")
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> delete(@PathVariable long noticeId) {
        opsNoticeService.delete(noticeId);
        return ResponseEntity.noContent().build();
    }

}
