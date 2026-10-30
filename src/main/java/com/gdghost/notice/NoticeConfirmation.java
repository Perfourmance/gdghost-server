package com.gdghost.notice;

import java.time.Instant;

/** 확인했어요 결과. confirmedAt은 처음 누른 시각 */
public record NoticeConfirmation(boolean confirmed, Instant confirmedAt) {
}
