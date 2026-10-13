package com.gdghost.auth;

import java.time.Instant;

public record VerificationSentResponse(Instant expiresAt, Instant resendAvailableAt) {
}
