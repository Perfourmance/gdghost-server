package com.gdghost.member;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** member_tag는 키만 있는 연결 테이블이라 엔티티 없이 JDBC로 쓴다 */
@Repository
public class MemberTagWriter {

    private final JdbcTemplate jdbc;

    public MemberTagWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replace(long memberId, List<Long> tagIds) {
        jdbc.update("DELETE FROM member_tag WHERE member_id = ?", memberId);
        add(memberId, tagIds);
    }

    public void add(long memberId, List<Long> tagIds) {
        jdbc.batchUpdate("INSERT INTO member_tag (member_id, tag_id) VALUES (?, ?)", tagIds, tagIds.size(),
                (ps, tagId) -> {
                    ps.setLong(1, memberId);
                    ps.setLong(2, tagId);
                });
    }

}
