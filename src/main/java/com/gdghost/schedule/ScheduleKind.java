package com.gdghost.schedule;

public enum ScheduleKind {
    CLUB, ACADEMIC, CONTEST, JOB;

    /** 동아리 · 학사는 운영진만 등록하고 공식 일정으로 저장한다 */
    boolean official() {
        return this == CLUB || this == ACADEMIC;
    }
}
