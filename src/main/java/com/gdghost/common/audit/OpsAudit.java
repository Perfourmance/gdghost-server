package com.gdghost.common.audit;

import java.util.Map;

/**
 * ops_audit_log 한 줄.
 * <pre>OpsAudit.of(actor.id(), OpsAction.SUSPEND).targetMember(memberId).detail(Map.of("reason", reason))</pre>
 * 탈퇴자가 대상이면 detail에 이메일 등 개인정보를 넣지 않는다 (ERD).
 */
public record OpsAudit(long actorId, OpsAction action, Long targetMemberId,
                       OpsTargetType targetType, Long targetId, Map<String, ?> detail) {

    public static OpsAudit of(long actorId, OpsAction action) {
        return new OpsAudit(actorId, action, null, null, null, Map.of());
    }

    public OpsAudit targetMember(long memberId) {
        return new OpsAudit(actorId, action, memberId, targetType, targetId, detail);
    }

    public OpsAudit target(OpsTargetType type, long id) {
        return new OpsAudit(actorId, action, targetMemberId, type, id, detail);
    }

    public OpsAudit detail(Map<String, ?> detail) {
        return new OpsAudit(actorId, action, targetMemberId, targetType, targetId, detail);
    }

}
