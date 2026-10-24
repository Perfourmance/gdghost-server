package com.gdghost.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gdghost.common.security.Role;

/**
 * 글 · 답변의 작성자. 익명이면 id 없이 label만, 탈퇴자는 id null (API 설계서 공통 객체).
 */
public sealed interface AuthorRef {

    String WITHDRAWN_NICKNAME = "탈퇴한 부원";

    record Named(boolean anonymous, long id, String nickname, int generation, boolean isStaff, boolean withdrawn)
            implements AuthorRef {
    }

    record Withdrawn(boolean anonymous, Long id, String nickname, boolean withdrawn) implements AuthorRef {
    }

    record Anonymous(boolean anonymous, String label, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean mine)
            implements AuthorRef {
    }

    /** 응답 모양을 고르기 위해 읽는 작성자 정보 */
    record Row(long id, String nickname, int generation, Role role, boolean withdrawn) {
    }

    static AuthorRef of(Row author, boolean anonymous, String label, long viewerId) {
        if (anonymous) {
            return new Anonymous(true, label, author.id() == viewerId ? Boolean.TRUE : null);
        }
        if (author.withdrawn()) {
            return new Withdrawn(false, null, WITHDRAWN_NICKNAME, true);
        }
        return new Named(false, author.id(), author.nickname(), author.generation(), author.role() == Role.STAFF,
                false);
    }

}
