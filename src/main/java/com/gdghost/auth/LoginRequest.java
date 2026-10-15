package com.gdghost.auth;

import jakarta.validation.constraints.NotBlank;

/** remember를 빼면 로그인 유지(true) */
public record LoginRequest(@NotBlank String loginId, @NotBlank String password, Boolean remember) {

    public boolean rememberOrDefault() {
        return remember == null || remember;
    }

}
