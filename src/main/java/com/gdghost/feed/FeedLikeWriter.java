package com.gdghost.feed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 좋아요 행과 like_count를 함께 바꾼다. 부르는 쪽이 글 행을 먼저 잠근다 */
@Repository
class FeedLikeWriter {

    private final JdbcTemplate jdbc;

    FeedLikeWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void like(long postId, long memberId) {
        int inserted = jdbc.update("INSERT IGNORE INTO feed_like (post_id, member_id) VALUES (?, ?)", postId, memberId);
        if (inserted > 0) {
            jdbc.update("UPDATE feed_post SET like_count = like_count + 1 WHERE id = ?", postId);
        }
    }

    void unlike(long postId, long memberId) {
        int deleted = jdbc.update("DELETE FROM feed_like WHERE post_id = ? AND member_id = ?", postId, memberId);
        if (deleted > 0) {
            jdbc.update("UPDATE feed_post SET like_count = like_count - 1 WHERE id = ?", postId);
        }
    }

    long count(long postId) {
        return jdbc.queryForObject("SELECT like_count FROM feed_post WHERE id = ?", Long.class, postId);
    }

}
