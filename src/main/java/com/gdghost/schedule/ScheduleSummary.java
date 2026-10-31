package com.gdghost.schedule;

import java.time.LocalDate;

/** dDay는 마감(종료일, 없으면 시작일)까지 남은 날, 지났으면 음수. deadlineSoon은 0–3일 */
public record ScheduleSummary(long id, ScheduleKind kind, boolean isOfficial, String title, LocalDate startDate,
        LocalDate endDate, String place, long dDay, boolean deadlineSoon, ScheduleAuthor author) {
}
