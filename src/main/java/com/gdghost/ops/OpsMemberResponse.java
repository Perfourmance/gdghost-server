package com.gdghost.ops;

import java.time.Instant;

import com.gdghost.common.security.Role;
import com.gdghost.member.Member;
import com.gdghost.member.MemberStatus;

/** 이메일은 담지 않는다 (가입 이메일 보기로만) */
public record OpsMemberResponse(long id, String name, String nickname, String loginId, int generation,
                                MemberStatus status, Role role, String staffTitle, boolean hiddenFromList,
                                Instant suspendedAt, Instant joinedAt, Instant lastSeenAt) {

    static OpsMemberResponse from(Member member) {
        return new OpsMemberResponse(member.getId(), member.getName(), member.getNickname(), member.getLoginId(),
                member.getGeneration(), member.getStatus(), member.getRole(), member.getStaffTitle(),
                member.isHiddenFromList(), member.getSuspendedAt(), member.getCreatedAt(), member.getLastSeenAt());
    }

}
