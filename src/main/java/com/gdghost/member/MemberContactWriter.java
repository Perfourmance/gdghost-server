package com.gdghost.member;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.gdghost.member.MyProfileResponse.Contact;

@Repository
public class MemberContactWriter {

    private final JdbcTemplate jdbc;

    public MemberContactWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replace(long memberId, List<Contact> contacts) {
        jdbc.update("DELETE FROM member_contact WHERE member_id = ?", memberId);
        jdbc.batchUpdate("INSERT INTO member_contact (member_id, type, value, is_public) VALUES (?, ?, ?, ?)",
                contacts, contacts.size(), (ps, contact) -> {
                    ps.setLong(1, memberId);
                    ps.setString(2, contact.type().name());
                    ps.setString(3, contact.value());
                    ps.setBoolean(4, contact.isPublic());
                });
    }

}
