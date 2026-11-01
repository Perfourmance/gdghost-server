package com.gdghost.coffee;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gdghost.member.MemberStatus;

/**
 * @param expiresAt  REQUESTED일 때만. 신청 + 7일
 * @param coffeeMemo ACCEPTED이고 수락 후 14일 안일 때만. 그 외에는 필드 자체가 없다
 */
public record CoffeeChatItem(long id, CoffeeChatState state, Counterpart counterpart, String purpose,
                             Instant createdAt, Instant respondedAt, Instant expiresAt,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String coffeeMemo) {

    /** 탈퇴한 상대는 id 없이 '탈퇴한 부원'만 */
    public record Counterpart(Long id, String nickname, Integer generation, MemberStatus status, String statusNote,
                              boolean withdrawn) {
    }

}
