package com.gdghost.member;

import java.time.Instant;
import java.util.List;

import com.gdghost.common.security.Role;

/** 프로필 수정 · 설정 화면용. 비공개 연락 수단도 본인에게는 준다 */
public record MyProfileResponse(long id, String loginId, String email, String name, String nickname, int generation,
        String studentNo, String department, Position position, MemberStatus status, Role role,
        List<Interest> interests, List<TagItem> experiences, String statusNote, Instant statusNoteUpdatedAt,
        String bio, List<Contact> contacts, SettingsResponse settings, Instant nicknameChangeAvailableAt,
        Instant emailChangeAvailableAt, ProfileCompleteness profileCompleteness) {

    public record Interest(long id, String name, List<TagItem> skills) {
    }

    public record TagItem(long id, String name) {
    }

    public record Contact(ContactType type, String value, boolean isPublic) {
    }

}
