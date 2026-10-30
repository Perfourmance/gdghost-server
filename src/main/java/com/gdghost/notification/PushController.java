package com.gdghost.notification;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "알림")
@RestController
public class PushController {

    private final PushSubscriptionService subscriptionService;
    private final String publicKey;

    public PushController(PushSubscriptionService subscriptionService,
            @Value("${push.vapid.public-key}") String publicKey) {
        this.subscriptionService = subscriptionService;
        this.publicKey = publicKey;
    }

    @Operation(summary = "웹 푸시 공개키", description = "브라우저가 구독할 때 쓰는 서버 공개키. 하루 캐시.")
    @GetMapping("/api/v1/push/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> vapidPublicKey() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(new VapidPublicKeyResponse(publicKey.isBlank() ? null : publicKey));
    }

    @Operation(summary = "웹 푸시 구독 등록", description = "기기마다 하나. 새로 등록하면 201, 이미 있는 endpoint면 200이고 "
            + "주인을 현재 부원으로 바꾼다. push_on도 켠다. 브라우저 푸시 서비스 주소가 아니면 400.")
    @PostMapping("/api/v1/me/push-subscriptions")
    public ResponseEntity<PushSubscriptionResponse> subscribe(@AuthenticationPrincipal AuthMember me,
            @Valid @RequestBody PushSubscriptionRequest request) {
        PushSubscriptionService.Saved saved = subscriptionService.subscribe(me.id(), request);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new PushSubscriptionResponse(saved.id()));
    }

    @Operation(summary = "웹 푸시 구독 해제", description = "이 기기의 푸시를 끈다. 본문에 endpoint. 없는 주소도 204. "
            + "모든 기기를 끄는 것은 설정 변경의 pushOn: false.")
    @DeleteMapping("/api/v1/me/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal AuthMember me,
            @Valid @RequestBody PushUnsubscribeRequest request) {
        subscriptionService.unsubscribe(me.id(), request.endpoint());
    }

}
