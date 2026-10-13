package com.gdghost.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백 · 대소문자는 서버가 정리한다 */
public record InviteCodeCheckRequest(@NotBlank @Size(max = 40) String code) {
}
