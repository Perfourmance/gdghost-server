package com.gdghost.auth;

import java.time.Instant;

public record InviteCodeCheckResponse(String signupToken, int generation, Instant expiresAt) {
}
