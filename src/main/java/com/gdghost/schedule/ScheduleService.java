package com.gdghost.schedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;
import com.gdghost.common.security.AuthMember;
import com.gdghost.common.security.Role;

@Service
public class ScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long MAX_RANGE_DAYS = 62;
    private static final long SOON_DAYS = 3;

    private final ScheduleRepository schedules;
    private final ScheduleReader reader;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ScheduleService(ScheduleRepository schedules, ScheduleReader reader, JdbcTemplate jdbc, Clock clock) {
        this.schedules = schedules;
        this.reader = reader;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScheduleListResponse list(AuthMember viewer, LocalDate from, LocalDate to, ScheduleKind kind) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("to", "기간은 최대 62일입니다.")));
        }
        return new ScheduleListResponse(items(viewer, reader.between(from, to, kind)));
    }

    /** 홈의 다가오는 일정 */
    @Transactional(readOnly = true)
    public List<?> upcoming(AuthMember viewer, int limit) {
        return items(viewer, reader.upcoming(today(), limit));
    }

    @Transactional(readOnly = true)
    public ScheduleDetail detail(long viewerId, long scheduleId) {
        return detail(found(scheduleId), viewerId);
    }

    @Transactional
    public ScheduleDetail create(AuthMember me, ScheduleCreateRequest request) {
        checkKind(me, request.kind());
        Schedule schedule = schedules.saveAndFlush(Schedule.write(me.id(), request.kind(), request.title(),
                request.startDate(), request.endDate(), request.place(), request.memo(), clock.instant()));
        return detail(schedule, me.id());
    }

    @Transactional
    public ScheduleDetail update(AuthMember me, long scheduleId, ScheduleUpdateRequest request) {
        Schedule schedule = writable(me, scheduleId);
        if (request.kind() != null) {
            checkKind(me, request.kind());
        }
        LocalDate startDate = request.startDate() != null ? request.startDate() : schedule.getStartDate();
        LocalDate endDate = request.clearingEndDate() ? null
                : request.endDate() != null ? request.endDate() : schedule.getEndDate();
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                    List.of(new ErrorField("endDate", "종료일은 시작일 이후여야 합니다.")));
        }
        schedule.edit(request.kind() != null ? request.kind() : schedule.getKind(),
                request.title() != null ? request.title() : schedule.getTitle(),
                startDate, endDate,
                request.place() != null ? ScheduleUpdateRequest.textOrNull(request.place()) : schedule.getPlace(),
                request.memo() != null ? ScheduleUpdateRequest.textOrNull(request.memo()) : schedule.getMemo(),
                clock.instant());
        return detail(schedule, me.id());
    }

    /** 연결된 모집은 남기고 연결만 끊는다 */
    @Transactional
    public void delete(AuthMember me, long scheduleId) {
        writable(me, scheduleId).delete(clock.instant());
        jdbc.update("UPDATE recruit SET schedule_id = NULL WHERE schedule_id = ?", scheduleId);
    }

    private Schedule writable(AuthMember me, long scheduleId) {
        Schedule schedule = found(scheduleId);
        if (schedule.getAuthorId() != me.id() && me.role() != Role.STAFF) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }
        return schedule;
    }

    private Schedule found(long scheduleId) {
        return schedules.findById(scheduleId)
                .filter(schedule -> !schedule.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static void checkKind(AuthMember me, ScheduleKind kind) {
        if (kind.official() && me.role() != Role.STAFF) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "동아리 · 학사 일정은 운영진만 등록할 수 있습니다.");
        }
    }

    private ScheduleDetail detail(Schedule schedule, long viewerId) {
        return new ScheduleDetail(schedule.getId(), schedule.getKind(), schedule.isOfficial(), schedule.getTitle(),
                schedule.getStartDate(), schedule.getEndDate(), schedule.getPlace(), schedule.getMemo(),
                reader.author(schedule.getAuthorId()), schedule.getAuthorId() == viewerId,
                reader.relatedRecruits(schedule.getId(), clock.instant()));
    }

    private List<?> items(AuthMember viewer, List<ScheduleReader.Row> rows) {
        if (viewer == null) {
            return rows.stream()
                    .map(row -> new GuestScheduleSummary(row.id(), row.kind(), row.title(), row.startDate(),
                            row.endDate()))
                    .toList();
        }
        LocalDate today = today();
        return rows.stream().map(row -> {
            long dDay = ChronoUnit.DAYS.between(today, row.deadline());
            return new ScheduleSummary(row.id(), row.kind(), row.official(), row.title(), row.startDate(),
                    row.endDate(), row.place(), dDay, dDay >= 0 && dDay <= SOON_DAYS, row.author());
        }).toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

}
