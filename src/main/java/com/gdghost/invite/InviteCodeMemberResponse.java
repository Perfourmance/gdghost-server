package com.gdghost.invite;

import java.time.Instant;

public record InviteCodeMemberResponse(long id, String name, String nickname, String studentNo, Instant joinedAt) {
}
