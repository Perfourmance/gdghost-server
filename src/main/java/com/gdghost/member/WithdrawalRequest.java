package com.gdghost.member;

import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(@NotBlank String password) {
}
