package com.gdghost.member;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.SessionCookie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "계정")
@RestController
@RequestMapping("/api/v1/me")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @Operation(summary = "탈퇴", description = "질문 · 답변 · 공유는 '탈퇴한 부원'으로 남고 피드 · 개인정보는 지운다. "
            + "모든 기기 로그아웃. 혼자 남은 운영진은 409. 비밀번호 10분에 5회 틀리면 429.")
    @PostMapping("/withdrawal")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthMember me,
            @RequestBody @Valid WithdrawalRequest request) {
        withdrawalService.withdraw(me.id(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.expired().toString())
                .build();
    }

}
