package com.gdghost.event;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
class EventWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    EventWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    long insert(long createdBy, String name, LocalDate startDate, LocalDate endDate, String description,
            String requirement, String guideSteps) {
        return new SimpleJdbcInsert(jdbc).withTableName("event").usingGeneratedKeyColumns("id")
                .usingColumns("created_by", "name", "start_date", "end_date", "description", "requirement",
                        "guide_steps", "created_at")
                .executeAndReturnKey(Map.of("created_by", createdBy, "name", name, "start_date", startDate,
                        "end_date", endDate, "description", description, "requirement", requirement,
                        "guide_steps", guideSteps, "created_at", now()))
                .longValue();
    }

    void update(long eventId, String name, LocalDate startDate, LocalDate endDate, String description,
            String requirement, String guideSteps) {
        jdbc.update("""
                UPDATE event
                SET name = ?, start_date = ?, end_date = ?, description = ?, requirement = ?, guide_steps = ?,
                    updated_at = ?
                WHERE id = ?
                """, name, startDate, endDate, description, requirement, guideSteps, now(), eventId);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

}
