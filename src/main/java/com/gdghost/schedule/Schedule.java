package com.gdghost.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long authorId;

    @Enumerated(EnumType.STRING)
    private ScheduleKind kind;

    @Column(name = "is_official")
    private boolean official;

    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String place;
    private String memo;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Schedule write(long authorId, ScheduleKind kind, String title, LocalDate startDate,
            LocalDate endDate, String place, String memo, Instant now) {
        Schedule schedule = new Schedule();
        schedule.authorId = authorId;
        schedule.kind = kind;
        schedule.official = kind.official();
        schedule.title = title;
        schedule.startDate = startDate;
        schedule.endDate = endDate;
        schedule.place = place;
        schedule.memo = memo;
        schedule.createdAt = now;
        return schedule;
    }

    /** 실제로 바뀐 게 있을 때만 수정 시각을 찍는다 */
    void edit(ScheduleKind kind, String title, LocalDate startDate, LocalDate endDate, String place, String memo,
            Instant now) {
        boolean changed = kind != this.kind || !title.equals(this.title) || !startDate.equals(this.startDate)
                || !Objects.equals(endDate, this.endDate) || !Objects.equals(place, this.place)
                || !Objects.equals(memo, this.memo);
        if (!changed) {
            return;
        }
        this.kind = kind;
        this.official = kind.official();
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.place = place;
        this.memo = memo;
        this.updatedAt = now;
    }

    void delete(Instant now) {
        deletedAt = now;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

}
