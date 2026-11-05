package com.gdghost.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** updatedAt은 고친 적 없으면 null. editedByStaff는 마지막 수정자가 작성자가 아니면 true */
public record ScheduleDetail(long id, ScheduleKind kind, boolean isOfficial, String title, LocalDate startDate,
        LocalDate endDate, String place, String memo, ScheduleAuthor author, boolean mine,
        Instant updatedAt, boolean editedByStaff, List<RelatedRecruit> relatedRecruits) {

    /** state는 정원이 찼거나 모집 시간이 지났으면 CLOSED */
    public record RelatedRecruit(long id, String title, int joinedCount, int capacity, String state) {
    }

}
