package com.gdghost.recruit;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RecruitWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    RecruitWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    void addMember(long recruitId, long memberId) {
        jdbc.update("INSERT INTO recruit_member (recruit_id, member_id, joined_at) VALUES (?, ?, ?)", recruitId,
                memberId, now());
    }

    void removeMember(long recruitId, long memberId) {
        jdbc.update("DELETE FROM recruit_member WHERE recruit_id = ? AND member_id = ?", recruitId, memberId);
    }

    /** @return 닫은 수 */
    int closePassed() {
        return jdbc.update("UPDATE recruit SET state = 'CLOSED' WHERE state = 'OPEN' AND meet_at <= ?", now());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

}
