package com.gdghost.member;

import java.util.ArrayList;
import java.util.List;

/** 6항목: 관심 분야 · 세부 기술 · 경험 태그 · 근황 · 자기소개 · 연락 수단 (AI 매칭 설계) */
public record ProfileCompleteness(int filled, int total, List<Item> missing) {

    static final int TOTAL = 6;

    public enum Item {
        INTEREST, SKILL, EXPERIENCE, STATUS_NOTE, BIO, CONTACT
    }

    public static ProfileCompleteness of(boolean interests, boolean skills, boolean experiences, boolean statusNote,
            boolean bio, boolean contact) {
        List<Item> missing = new ArrayList<>();
        addIfMissing(missing, interests, Item.INTEREST);
        addIfMissing(missing, skills, Item.SKILL);
        addIfMissing(missing, experiences, Item.EXPERIENCE);
        addIfMissing(missing, statusNote, Item.STATUS_NOTE);
        addIfMissing(missing, bio, Item.BIO);
        addIfMissing(missing, contact, Item.CONTACT);
        return new ProfileCompleteness(TOTAL - missing.size(), TOTAL, List.copyOf(missing));
    }

    private static void addIfMissing(List<Item> missing, boolean filled, Item item) {
        if (!filled) {
            missing.add(item);
        }
    }

}
