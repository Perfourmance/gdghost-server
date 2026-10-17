package com.gdghost.ops;

import java.time.Instant;

import com.gdghost.member.Member;

public record SuspensionResponse(long id, String nickname, Instant suspendedAt, String suspendedReason,
                                 SuspendedBy suspendedBy) {

    /** 처음 정지한 운영진. 이미 정지된 부원을 다시 요청하면 그 사이 탈퇴했을 수 있어 nickname이 null일 수 있다 */
    public record SuspendedBy(long id, String nickname) {
    }

    static SuspensionResponse of(Member member, Member staff) {
        return new SuspensionResponse(member.getId(), member.getNickname(), member.getSuspendedAt(),
                member.getSuspendedReason(), new SuspendedBy(staff.getId(), staff.getNickname()));
    }

}
