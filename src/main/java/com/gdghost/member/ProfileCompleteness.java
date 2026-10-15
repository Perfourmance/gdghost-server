package com.gdghost.member;

import java.util.stream.Stream;

/** 6항목: 관심 분야 · 세부 기술 · 경험 태그 · 근황 · 자기소개 · 연락 수단 (AI 매칭 설계) */
public record ProfileCompleteness(int filled, int total) {

    static final int TOTAL = 6;

    public static ProfileCompleteness of(boolean interests, boolean skills, boolean experiences, boolean statusNote,
            boolean bio, boolean contact) {
        int filled = (int) Stream.of(interests, skills, experiences, statusNote, bio, contact)
                .filter(Boolean::booleanValue).count();
        return new ProfileCompleteness(filled, TOTAL);
    }

}
