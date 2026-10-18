package com.gdghost.member;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "계정")
@RestController
@RequestMapping("/api/v1/me")
public class MyProfileController {

    private final MyProfileService myProfileService;

    public MyProfileController(MyProfileService myProfileService) {
        this.myProfileService = myProfileService;
    }

    @Operation(summary = "내 프로필 · 설정 조회", description = "프로필 수정 · 설정 화면용. 비공개 연락 수단도 준다.")
    @GetMapping
    public ResponseEntity<MyProfileResponse> me(@AuthenticationPrincipal AuthMember me) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(myProfileService.read(me.id()));
    }

    @Operation(summary = "설정 변경", description = "보낸 필드만 바꾼다. coffeeMemo는 빈 문자열이면 지운다. "
            + "커피챗을 켜 둔 채 메모가 없으면 400.")
    @PatchMapping("/settings")
    public SettingsResponse changeSettings(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid SettingsRequest request) {
        return myProfileService.changeSettings(me.id(), request);
    }

}
