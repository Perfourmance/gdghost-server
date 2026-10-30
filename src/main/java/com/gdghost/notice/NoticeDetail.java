package com.gdghost.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 공지 상세. stats는 운영진 · 개발자에게만, 아니면 null */
public record NoticeDetail(
        long id,
        String title,
        String body,
        boolean isPinned,
        boolean isMustRead,
        LocalDate dueDate,
        Author author,
        Instant createdAt,
        boolean confirmed,
        List<Comment> comments,
        NoticeStats stats) {

    /** 탈퇴했으면 id 없이 '탈퇴한 부원'. staffTitle은 지금 직함 (해제됐으면 null) */
    public record Author(Long id, String nickname, String staffTitle, boolean withdrawn) {

        static final String WITHDRAWN_NICKNAME = "탈퇴한 부원";

        static Author of(long id, String nickname, String staffTitle, boolean withdrawn) {
            return withdrawn ? new Author(null, WITHDRAWN_NICKNAME, null, true)
                    : new Author(id, nickname, staffTitle, false);
        }

    }

    public record Comment(long id, CommentAuthor author, String body, boolean mine, Instant createdAt) {
    }

    /** 탈퇴했으면 id · 기수 없이 '탈퇴한 부원' */
    public record CommentAuthor(Long id, String nickname, Integer generation, boolean withdrawn) {

        static CommentAuthor of(long id, String nickname, Integer generation, boolean withdrawn) {
            return withdrawn ? new CommentAuthor(null, Author.WITHDRAWN_NICKNAME, null, true)
                    : new CommentAuthor(id, nickname, generation, false);
        }

    }

}
