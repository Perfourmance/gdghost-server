package com.gdghost.member;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보낸 필드만 바꾼다(null은 그대로). 태그 목록은 보낸 종류만 통째로 교체, statusNote · bio는 빈 문자열이면 지운다.
 * 이름 · 기수 · 학번은 바꿀 수 없어 보내면 400.
 */
public record ProfileUpdateRequest(
        @Null(message = "이름은 바꿀 수 없습니다.") String name,
        @Null(message = "기수는 바꿀 수 없습니다.") Integer generation,
        @Null(message = "학번은 바꿀 수 없습니다.") String studentNo,
        @Size(min = 2, max = 12) String nickname,
        @Size(max = 30) @Pattern(regexp = ".*\\S.*", message = "비워 둘 수 없습니다.") String department,
        Position position,
        MemberStatus status,
        List<@NotNull Long> interestTagIds,
        List<@NotNull Long> skillTagIds,
        List<@NotNull Long> experienceTagIds,
        @Size(max = 60) String statusNote,
        @Size(max = 120) String bio) {

    boolean hasTagChange() {
        return interestTagIds != null || skillTagIds != null || experienceTagIds != null;
    }

}
