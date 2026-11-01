package com.gdghost.coffee;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "커피챗")
@SecurityRequirement(name = "sessionCookie")
@RestController
@RequestMapping("/api/v1")
public class CoffeeChatController {

    private final CoffeeChatService coffeeChatService;

    public CoffeeChatController(CoffeeChatService coffeeChatService) {
        this.coffeeChatService = coffeeChatService;
    }

    @Operation(summary = "커피챗 신청", description = "Idempotency-Key 필수. 목적 10–200자(앞뒤 공백 뗌), 부원당 분당 5회. "
            + "본인은 400, 없거나 탈퇴 · 정지한 상대는 404. 상대가 꺼 둠 · 응답 대기 중 · 거절 · 만료 후 14일 안이면 409. "
            + "받는 사람에게 앱 내 알림 + 웹 푸시.")
    @Idempotent
    @RateLimit(limit = 5)
    @PostMapping("/coffee-chats")
    public ResponseEntity<CoffeeChatItem> request(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid CoffeeChatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coffeeChatService.request(me.id(), request.receiverId(), request.purpose()));
    }

    @Operation(summary = "커피챗 수락 · 거절", description = "받은 사람만(아니면 403). 이미 응답했거나 7일이 지났으면 409. "
            + "수락하면 지금의 메모를 복사하고, 메모가 없으면 400. 신청자에게 앱 내 알림 + 웹 푸시.")
    @PatchMapping("/coffee-chats/{coffeeChatId}")
    public CoffeeChatItem respond(@AuthenticationPrincipal AuthMember me, @PathVariable long coffeeChatId,
            @RequestBody @Valid CoffeeChatRespondRequest request) {
        return coffeeChatService.respond(me.id(), coffeeChatId, request.action());
    }

    @Operation(summary = "내 커피챗 목록", description = "받은 신청 / 보낸 신청, 최신순. coffeeMemo는 ACCEPTED이고 수락 후 14일 안일 때만 있다.")
    @GetMapping("/me/coffee-chats")
    public CursorPage<CoffeeChatItem> list(@AuthenticationPrincipal AuthMember me, @RequestParam CoffeeChatBox box,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(CursorPage.MAX_SIZE) int size) {
        return coffeeChatService.list(me.id(), box, cursor, size);
    }

}
