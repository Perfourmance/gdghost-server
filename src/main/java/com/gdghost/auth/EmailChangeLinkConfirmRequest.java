package com.gdghost.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** token — 메일 링크의 ?token= 값 */
public record EmailChangeLinkConfirmRequest(@NotBlank @Size(max = 100) String token) {
}
