package com.gdghost.notice;

import java.time.Instant;

public record NoticeReminderResponse(int targetCount, Instant remindedAt) {
}
