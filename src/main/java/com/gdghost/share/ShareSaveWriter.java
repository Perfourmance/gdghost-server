package com.gdghost.share;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 저장 행과 save_count를 함께 바꾼다. 부르는 쪽이 글 행을 먼저 잠근다 */
@Repository
class ShareSaveWriter {

    private final JdbcTemplate jdbc;

    ShareSaveWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void save(long shareId, long memberId, Instant now) {
        int inserted = jdbc.update("INSERT IGNORE INTO share_save (share_id, member_id, created_at) VALUES (?, ?, ?)",
                shareId, memberId, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        if (inserted > 0) {
            jdbc.update("UPDATE share SET save_count = save_count + 1 WHERE id = ?", shareId);
        }
    }

    void unsave(long shareId, long memberId) {
        int deleted = jdbc.update("DELETE FROM share_save WHERE share_id = ? AND member_id = ?", shareId, memberId);
        if (deleted > 0) {
            jdbc.update("UPDATE share SET save_count = save_count - 1 WHERE id = ?", shareId);
        }
    }

}
