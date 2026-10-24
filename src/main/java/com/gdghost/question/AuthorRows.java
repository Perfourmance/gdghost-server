package com.gdghost.question;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.gdghost.common.security.Role;

/** 작성자 컬럼(nickname · generation · role · withdrawn_at)을 함께 SELECT한 행에서 읽는다 */
final class AuthorRows {

    static final String COLUMNS = " m.nickname, m.generation, m.role, m.withdrawn_at IS NOT NULL AS withdrawn ";

    private AuthorRows() {
    }

    static AuthorRef.Row read(ResultSet rs, String idColumn) throws SQLException {
        return new AuthorRef.Row(rs.getLong(idColumn), rs.getString("nickname"), rs.getInt("generation"),
                Role.valueOf(rs.getString("role")), rs.getBoolean("withdrawn"));
    }

}
