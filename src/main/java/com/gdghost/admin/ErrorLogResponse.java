package com.gdghost.admin;

import java.time.Instant;

public final class ErrorLogResponse {

    private ErrorLogResponse() {
    }

    public record Item(long id, String fingerprint, String message, String path, int count, Instant firstAt,
                       Instant lastAt) {
    }

    public record Detail(long id, String fingerprint, String message, String path, int count, Instant firstAt,
                         Instant lastAt, String stack) {
    }

}
