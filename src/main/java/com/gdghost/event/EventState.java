package com.gdghost.event;

import java.time.LocalDate;

/** 컬럼이 아니라 오늘(한국 날짜)로 계산한다 */
public enum EventState {
    UPCOMING, ONGOING, ENDED;

    static EventState of(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        return today.isAfter(endDate) ? ENDED : ONGOING;
    }
}
