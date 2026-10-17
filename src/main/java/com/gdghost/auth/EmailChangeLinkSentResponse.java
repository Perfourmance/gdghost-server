package com.gdghost.auth;

import java.time.Instant;

public record EmailChangeLinkSentResponse(Instant expiresAt) {
}
