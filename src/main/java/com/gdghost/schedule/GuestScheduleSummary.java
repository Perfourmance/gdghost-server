package com.gdghost.schedule;

import java.time.LocalDate;

public record GuestScheduleSummary(long id, ScheduleKind kind, String title, LocalDate startDate, LocalDate endDate) {
}
