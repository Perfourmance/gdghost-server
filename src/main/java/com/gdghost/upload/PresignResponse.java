package com.gdghost.upload;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PresignResponse(List<Item> items) {

    public record Item(String objectKey, String uploadUrl, String method, Map<String, String> headers,
            Instant expiresAt) {
    }

}
