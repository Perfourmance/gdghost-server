package com.gdghost.member;

/** 가입 때 받는 프로필. 선택 항목(statusNote · bio)은 비었으면 null */
public record MemberProfile(String name, String nickname, Position position, String studentNo, String department,
        MemberStatus status, String statusNote, String bio) {
}
