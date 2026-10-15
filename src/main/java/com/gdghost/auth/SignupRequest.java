package com.gdghost.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 비밀번호 규칙 위반은 VALIDATION_FAILED가 아니라 PASSWORD_POLICY라 여기서 검사하지 않는다 */
public record SignupRequest(
        @NotBlank String signupToken,
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{4,16}", message = "영문 · 숫자 4–16자여야 합니다.") String loginId,
        @NotNull String password,
        @NotNull @Valid SignupProfileRequest profile) {
}
