package com.gdghost.ops;

import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gdghost.common.audit.OpsAction;
import com.gdghost.common.page.OpsPage;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** ops_audit_log는 OpsAuditLogger가 JDBC로 쓰므로 읽기도 JDBC */
@Component
class OpsAuditLogReader {

    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    OpsAuditLogReader(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 최신순. 인덱스 (target_member_id, created_at) */
    OpsPage<OpsAuditLogResponse> findByTargetMember(long memberId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log WHERE target_member_id = ?",
                Long.class, memberId);
        List<OpsAuditLogResponse> items = jdbc.query("""
                SELECT l.id, l.action, l.detail, l.created_at, l.actor_id, a.nickname, a.staff_title
                FROM ops_audit_log l JOIN member a ON a.id = l.actor_id
                WHERE l.target_member_id = ?
                ORDER BY l.created_at DESC, l.id DESC
                LIMIT ? OFFSET ?
                """, (rs, row) -> {
            String detail = rs.getString("detail");
            return new OpsAuditLogResponse(
                    rs.getLong("id"),
                    OpsAction.valueOf(rs.getString("action")),
                    new OpsAuditLogResponse.Actor(rs.getLong("actor_id"), rs.getString("nickname"),
                            rs.getString("staff_title")),
                    detail == null ? Map.of() : json.readValue(detail, DETAIL),
                    rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
        }, memberId, size, (long) page * size);
        return new OpsPage<>(items, page, size, total);
    }

}
