package com.gdghost.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gdghost.common.ratelimit.RateLimit;
import com.gdghost.common.security.AuthMember;
import com.gdghost.member.MemberRepository;
import com.gdghost.member.Nicknames;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 입력 중 바로 알려 주는 용도. 최종 확인은 가입 · 수정 API가 다시 한다 */
@Tag(name = "가입")
@RestController
@RequestMapping("/api/v1/auth")
public class AvailabilityController {

    private final MemberRepository members;

    public AvailabilityController(MemberRepository members) {
        this.members = members;
    }

    @Operation(summary = "아이디 사용 가능 확인", description = "대소문자만 다른 아이디도 쓰는 것으로 본다. IP당 분당 30회.")
    @RateLimit(limit = 30, per = RateLimit.Per.IP)
    @GetMapping("/login-ids/availability")
    public LoginIdAvailabilityResponse loginId(
            @RequestParam @Pattern(regexp = "[A-Za-z0-9]{4,16}", message = "영문 · 숫자 4–16자여야 합니다.")
            String loginId) {
        return new LoginIdAvailabilityResponse(!members.existsByLoginId(loginId));
    }

    @Operation(summary = "닉네임 사용 가능 확인", description = "로그인한 부원이 자기 닉네임을 넣으면 사용 가능. IP당 분당 30회.")
    @RateLimit(limit = 30, per = RateLimit.Per.IP)
    @GetMapping("/nicknames/availability")
    public NicknameAvailabilityResponse nickname(@RequestParam @Size(min = 2, max = 12) String nickname,
            @AuthenticationPrincipal AuthMember me) {
        String trimmed = nickname.strip();
        if (Nicknames.isReserved(trimmed)) {
            return NicknameAvailabilityResponse.no(NicknameAvailabilityResponse.Reason.RESERVED);
        }
        boolean taken = me != null
                ? members.existsByNicknameAndIdNot(trimmed, me.id())
                : members.existsByNickname(trimmed);
        return taken ? NicknameAvailabilityResponse.no(NicknameAvailabilityResponse.Reason.TAKEN)
                : NicknameAvailabilityResponse.ok();
    }

}
