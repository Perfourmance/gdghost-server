package com.gdghost.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 비밀번호 규칙 위반은 VALIDATION_FAILED가 아니라 PASSWORD_POLICY라 여기서 검사하지 않는다 */
public record PasswordChangeRequest(@NotBlank String currentPassword, @NotNull String newPassword) {
}
