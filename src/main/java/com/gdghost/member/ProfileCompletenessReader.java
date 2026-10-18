package com.gdghost.member;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProfileCompletenessReader {

    private final JdbcTemplate jdbc;

    public ProfileCompletenessReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProfileCompleteness read(Member member) {
        return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                              WHERE mt.member_id = ? AND t.kind = 'INTEREST') AS interests,
                       EXISTS(SELECT 1 FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                              WHERE mt.member_id = ? AND t.kind = 'SKILL') AS skills,
                       EXISTS(SELECT 1 FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                              WHERE mt.member_id = ? AND t.kind = 'EXPERIENCE') AS experiences,
                       EXISTS(SELECT 1 FROM member_contact WHERE member_id = ?) AS contact
                """, (rs, i) -> ProfileCompleteness.of(rs.getBoolean("interests"), rs.getBoolean("skills"),
                rs.getBoolean("experiences"), member.getStatusNote() != null, member.getBio() != null,
                rs.getBoolean("contact")), member.getId(), member.getId(), member.getId(), member.getId());
    }

}
