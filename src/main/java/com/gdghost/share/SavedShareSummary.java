package com.gdghost.share;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** 공유 목록 항목 + 저장한 시각 */
public record SavedShareSummary(@JsonUnwrapped ShareSummary share, Instant savedAt) {
}
