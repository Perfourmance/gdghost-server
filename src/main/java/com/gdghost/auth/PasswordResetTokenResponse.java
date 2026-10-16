package com.gdghost.auth;

import java.time.Instant;

public record PasswordResetTokenResponse(String resetToken, Instant expiresAt) {
}
