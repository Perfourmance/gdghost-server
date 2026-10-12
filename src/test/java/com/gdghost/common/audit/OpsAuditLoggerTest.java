package com.gdghost.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.gdghost.support.Fixtures;
import com.gdghost.support.Fixtures.TestMember;
import com.gdghost.support.IntegrationTest;

@IntegrationTest
class OpsAuditLoggerTest {

    @Autowired
    OpsAuditLogger opsAudit;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Fixtures fixtures;

    @Test
    void 부원_대상_행동을_사유와_함께_기록한다() {
        TestMember staff = fixtures.staff();
        TestMember member = fixtures.member();

        tx.executeWithoutResult(status -> opsAudit.record(OpsAudit.of(staff.id(), OpsAction.SUSPEND)
                .targetMember(member.id())
                .detail(Map.of("reason", "유출된 초대 코드"))));

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT actor_id, action, target_member_id, target_type, detail->>'$.reason' AS reason
                FROM ops_audit_log
                """);
        assertThat(row).containsEntry("actor_id", staff.id())
                .containsEntry("action", "SUSPEND")
                .containsEntry("target_member_id", member.id())
                .containsEntry("target_type", null)
                .containsEntry("reason", "유출된 초대 코드");
    }

    @Test
    void 부원이_아닌_대상은_종류와_id로_가리킨다() {
        TestMember staff = fixtures.staff();

        tx.executeWithoutResult(status -> opsAudit.record(OpsAudit.of(staff.id(), OpsAction.INVITE_CODE_REVOKE)
                .target(OpsTargetType.INVITE_CODE, 7)));

        Map<String, Object> row = jdbc.queryForMap("SELECT target_type, target_id, detail FROM ops_audit_log");
        assertThat(row).containsEntry("target_type", "INVITE_CODE")
                .containsEntry("target_id", 7L)
                .containsEntry("detail", null);
    }

    @Test
    void 행동이_롤백되면_기록도_남지_않는다() {
        TestMember staff = fixtures.staff();

        tx.executeWithoutResult(status -> {
            opsAudit.record(OpsAudit.of(staff.id(), OpsAction.ROUTING_EXPORT));
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ops_audit_log", Long.class)).isZero();
    }

    @Test
    void 트랜잭션_밖에서는_기록할_수_없다() {
        TestMember staff = fixtures.staff();

        assertThatThrownBy(() -> opsAudit.record(OpsAudit.of(staff.id(), OpsAction.EMAIL_VIEW)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

}
