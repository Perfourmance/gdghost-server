package com.gdghost.ops;

import com.gdghost.common.security.Role;
import com.gdghost.member.Member;

public record StaffResponse(long id, String nickname, Role role, String staffTitle) {

    static StaffResponse from(Member member) {
        return new StaffResponse(member.getId(), member.getNickname(), member.getRole(), member.getStaffTitle());
    }

}
