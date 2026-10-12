package com.gdghost.common.audit;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * 운영 기록의 원본은 DB — 디스코드는 편의용 알림.
 * 행동과 기록이 함께 커밋 · 롤백되도록 트랜잭션 밖에서 부르면 예외(MANDATORY).
 */
@Component
public class OpsAuditLogger {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public OpsAuditLogger(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OpsAudit audit) {
        jdbc.update("""
                INSERT INTO ops_audit_log (actor_id, action, target_member_id, target_type, target_id, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                audit.actorId(),
                audit.action().name(),
                audit.targetMemberId(),
                audit.targetType() == null ? null : audit.targetType().name(),
                audit.targetId(),
                audit.detail() == null || audit.detail().isEmpty() ? null : json.writeValueAsString(audit.detail()),
                LocalDateTime.now(clock));
    }

}
