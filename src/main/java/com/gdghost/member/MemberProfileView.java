package com.gdghost.member;

import java.time.Instant;
import java.util.List;

/** 다른 부원이 보는 프로필. 실명 · 학과 · 학번은 부원에게만 (이 API는 부원 이상만 부른다) */
public record MemberProfileView(long id, String nickname, String name, int generation, String studentNo,
        String department, Position position, MemberStatus status, boolean isStaff, List<Interest> interests,
        List<String> experiences, String statusNote, Instant statusNoteUpdatedAt, String bio,
        List<Contact> contacts, boolean acceptQuestion, boolean acceptCoffee, CoffeeChat coffeeChat,
        MemberStats stats) {

    public record Interest(String name, List<String> skills) {
    }

    public record Contact(ContactType type, String value) {
    }

    /** 내가 보낸 마지막 신청의 상태. availableAt은 거절 · 만료 후 14일 안일 때 다시 신청할 수 있는 시각 */
    public record CoffeeChat(String myRequestState, Instant availableAt) {

        static final CoffeeChat NONE = new CoffeeChat(null, null);

    }

}
