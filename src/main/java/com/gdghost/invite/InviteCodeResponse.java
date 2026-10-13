package com.gdghost.invite;

import java.time.Instant;

public record InviteCodeResponse(long id, String code, int generation, int maxUses, int usedCount,
                                 Instant expiresAt, Instant revokedAt, InviteCodeState state,
                                 Creator createdBy, Instant createdAt) {

    /** 준비용 코드는 null */
    public record Creator(long id, String nickname) {
    }

    static InviteCodeResponse of(InviteCode code, String creatorNickname, Instant now) {
        return new InviteCodeResponse(code.getId(), code.getCode(), code.getGeneration(), code.getMaxUses(),
                code.getUsedCount(), code.getExpiresAt(), code.getRevokedAt(), code.state(now),
                code.getCreatedBy() == null ? null : new Creator(code.getCreatedBy(), creatorNickname),
                code.getCreatedAt());
    }

}
