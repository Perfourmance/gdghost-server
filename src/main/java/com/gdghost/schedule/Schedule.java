package com.gdghost.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private Long updatedBy;
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

    /** 실제로 바뀐 게 있을 때만 수정 시각 · 수정자를 남긴다. 바뀐 필드 이름을 돌려준다(운영 기록용) */
    List<String> edit(ScheduleKind kind, String title, LocalDate startDate, LocalDate endDate, String place,
            String memo, long editorId, Instant now) {
        List<String> changed = new ArrayList<>();
        if (kind != this.kind) {
            changed.add("kind");
        }
        if (!title.equals(this.title)) {
            changed.add("title");
        }
        if (!startDate.equals(this.startDate)) {
            changed.add("startDate");
        }
        if (!Objects.equals(endDate, this.endDate)) {
            changed.add("endDate");
        }
        if (!Objects.equals(place, this.place)) {
            changed.add("place");
        }
        if (!Objects.equals(memo, this.memo)) {
            changed.add("memo");
        }
        if (changed.isEmpty()) {
            return changed;
        }
        this.kind = kind;
        this.official = kind.official();
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.place = place;
        this.memo = memo;
        this.updatedAt = now;
        this.updatedBy = editorId;
        return changed;
    }

    /** 운영진만 남의 일정을 고칠 수 있으므로 마지막 수정자가 작성자가 아니면 운영진 */
    boolean editedByStaff() {
        return updatedBy != null && updatedBy != authorId;
    }

    void delete(Instant now) {
        deletedAt = now;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

}
