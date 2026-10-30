package com.gdghost.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.page.CursorPage;
import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "알림")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "알림 목록", description = "나에게 온 앱 내 알림, 최신순. 열어도 읽음 처리하지 않는다. "
            + "화면 경로는 target으로 프론트가 만든다. 90일 지나면 지워진다.")
    @GetMapping
    public CursorPage<NotificationResponse> list(@AuthenticationPrincipal AuthMember me,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return notificationService.list(me.id(), cursor, size);
    }

    @Operation(summary = "알림 읽음 처리", description = "ids(최대 100개)와 all(true) 중 하나. "
            + "남의 알림 id는 조용히 무시한다. 응답은 남은 안 읽은 수.")
    @PostMapping("/read")
    public NotificationReadResponse read(@AuthenticationPrincipal AuthMember me,
            @Valid @RequestBody NotificationReadRequest request) {
        return notificationService.read(me.id(), request);
    }

    @Operation(summary = "안 읽은 알림 수")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthMember me) {
        return notificationService.unreadCount(me.id());
    }

}
