package com.gdghost.ops;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class GraduationWriter {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    GraduationWriter(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 활동 중인 재학 · 휴학만 잠그고 졸업으로 바꾼다. @return 바뀐 부원 id */
    List<Long> graduate(List<Long> memberIds) {
        List<Long> targets = jdbc.queryForList("""
                SELECT id FROM member
                WHERE id IN (:ids) AND status <> 'GRADUATED' AND withdrawn_at IS NULL AND suspended_at IS NULL
                ORDER BY id
                FOR UPDATE
                """, Map.of("ids", memberIds), Long.class);
        if (!targets.isEmpty()) {
            jdbc.update("UPDATE member SET status = 'GRADUATED', updated_at = :now WHERE id IN (:ids)",
                    Map.of("now", LocalDateTime.now(clock), "ids", targets));
        }
        return targets;
    }

}
