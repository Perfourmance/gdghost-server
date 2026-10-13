package com.gdghost.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupEmailConfirmRequest(
        @NotBlank String signupToken,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "숫자 6자리여야 합니다.") String code) {
}
