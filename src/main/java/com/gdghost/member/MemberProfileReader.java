package com.gdghost.member;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.member.MyProfileResponse.Contact;
import com.gdghost.member.MyProfileResponse.Interest;
import com.gdghost.member.MyProfileResponse.TagItem;

/** member_tag · member_contact는 엔티티 없이 JDBC로 읽는다 */
@Repository
public class MemberProfileReader {

    private final JdbcTemplate jdbc;

    public MemberProfileReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Tags(List<Interest> interests, List<TagItem> experiences) {
    }

    /** 세부 기술은 관심 분야 아래에 묶는다 */
    public Tags tags(long memberId) {
        Map<Long, List<TagItem>> skillsByInterest = new LinkedHashMap<>();
        Map<Long, String> interestNames = new LinkedHashMap<>();
        List<TagItem> experiences = new ArrayList<>();
        jdbc.query("""
                SELECT t.id, t.kind, t.parent_id, t.name
                FROM member_tag mt JOIN tag t ON t.id = mt.tag_id
                WHERE mt.member_id = ?
                ORDER BY t.sort_order, t.id
                """, rs -> {
            long id = rs.getLong("id");
            String name = rs.getString("name");
            switch (rs.getString("kind")) {
                case "INTEREST" -> {
                    interestNames.put(id, name);
                    skillsByInterest.putIfAbsent(id, new ArrayList<>());
                }
                case "SKILL" -> skillsByInterest.computeIfAbsent(rs.getLong("parent_id"), k -> new ArrayList<>())
                        .add(new TagItem(id, name));
                default -> experiences.add(new TagItem(id, name));
            }
        }, memberId);
        List<Interest> interests = interestNames.entrySet().stream()
                .map(e -> new Interest(e.getKey(), e.getValue(), skillsByInterest.get(e.getKey())))
                .toList();
        return new Tags(interests, experiences);
    }

    public List<Contact> contacts(long memberId) {
        return jdbc.query("SELECT type, value, is_public FROM member_contact WHERE member_id = ? ORDER BY type",
                (rs, i) -> new Contact(ContactType.valueOf(rs.getString("type")), rs.getString("value"),
                        rs.getBoolean("is_public")),
                memberId);
    }

}
