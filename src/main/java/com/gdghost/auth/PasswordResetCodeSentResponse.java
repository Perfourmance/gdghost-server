package com.gdghost.auth;

import java.time.Instant;

public record PasswordResetCodeSentResponse(Instant resendAvailableAt) {
}
