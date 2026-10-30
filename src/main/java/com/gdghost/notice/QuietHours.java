package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** 푸시를 보내지 않는 시간. 22시부터 다음 날 08시 전까지(한국 시간) */
final class QuietHours {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime START = LocalTime.of(22, 0);
    private static final LocalTime END = LocalTime.of(8, 0);

    private QuietHours() {
    }

    static boolean contains(Instant instant) {
        LocalTime time = instant.atZone(KST).toLocalTime();
        return !time.isBefore(START) || time.isBefore(END);
    }

    /** 이번 아침 08시에 끝나는 구간의 시작 */
    static Instant lastStart(Instant now) {
        return LocalDate.ofInstant(now, KST).minusDays(1).atTime(START).atZone(KST).toInstant();
    }

    static Instant lastEnd(Instant now) {
        return LocalDate.ofInstant(now, KST).atTime(END).atZone(KST).toInstant();
    }

}
