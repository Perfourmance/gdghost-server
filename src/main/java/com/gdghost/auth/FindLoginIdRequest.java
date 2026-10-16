package com.gdghost.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FindLoginIdRequest(@NotBlank @Email @Size(max = 254) String email) {
}
