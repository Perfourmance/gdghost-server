package com.gdghost.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gdghost.member.Member;
import com.gdghost.member.ProfileCompleteness;

/** 가입 완료 · 로그인 · 내 세션 확인의 Me 응답 */
@Component
public class MeReader {

    private final JdbcTemplate jdbc;

    public MeReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // TODO 안 읽은 알림(6단계) · 확인 안 한 필독 공지(5단계) 수
    public MeResponse read(Member member) {
        return new MeResponse(member.getId(), member.getLoginId(), member.getNickname(), member.getGeneration(),
                member.getRole(), member.getStaffTitle(), 0, 0, completeness(member));
    }

    private ProfileCompleteness completeness(Member member) {
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
