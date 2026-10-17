package com.gdghost.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * currentPassword — 요청하는 개발자 본인의 비밀번호.
 * identityNote — 본인 확인 방법 메모. 예) 본인 디스코드 DM으로 요청, 얼굴 아는 사이
 */
public record StaffEmailChangeRequest(
        @NotBlank @Size(max = 64) String currentPassword,
        @NotBlank @Email @Size(max = 254) String newEmail,
        @NotBlank @Size(min = 5, max = 100) String identityNote) {
}
