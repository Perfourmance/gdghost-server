package com.gdghost.share;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ShareTagWriter {

    private final JdbcTemplate jdbc;

    ShareTagWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** PK가 대소문자를 구분하지 않아 Java · java는 하나만 남는다 — IGNORE가 없으면 충돌로 500 */
    void replace(long shareId, List<String> tags) {
        jdbc.update("DELETE FROM share_tag WHERE share_id = ?", shareId);
        for (String tag : tags) {
            jdbc.update("INSERT IGNORE INTO share_tag (share_id, name) VALUES (?, ?)", shareId, tag);
        }
    }

}
