package com.gdghost.auth;

import java.util.List;

import com.gdghost.member.MemberStatus;
import com.gdghost.member.Position;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 태그 개수 · 종류는 TagService가 검사한다 */
public record SignupProfileRequest(
        @NotBlank @Size(max = 20) String name,
        @NotBlank @Size(min = 2, max = 12) String nickname,
        @NotNull Position position,
        @NotNull @Pattern(regexp = "\\d{2}", message = "숫자 2자리여야 합니다.") String studentNo,
        @NotBlank @Size(max = 30) String department,
        @NotNull MemberStatus status,
        @NotNull List<@NotNull Long> interestTagIds,
        List<@NotNull Long> skillTagIds,
        List<@NotNull Long> experienceTagIds,
        @Size(max = 60) String statusNote,
        @Size(max = 120) String bio) {
}
