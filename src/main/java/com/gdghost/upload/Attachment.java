package com.gdghost.upload;

import java.time.Instant;

/** 응답에 담는 사진. url은 서명 주소, urlExpiresAt은 정각 단위 */
public record Attachment(String objectKey, String url, Instant urlExpiresAt, int sortOrder) {
}
