package com.gdghost.search;

import java.util.List;

/** 종류별 상위 항목과 전체 건수 */
public record SearchAllResponse(Group members, Group questions, Group shares, Group schedules) {

    public record Group(List<?> items, long total) {
    }

}
