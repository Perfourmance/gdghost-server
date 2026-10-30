package com.gdghost.notification;

import java.time.Instant;

/** @param type 프론트는 모르는 값을 일반 알림으로 표시한다 */
public record NotificationResponse(long id, String type, String title, String body, NotificationTarget target,
        boolean read, Instant createdAt) {
}
