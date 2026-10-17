package com.gdghost.ops;

import java.time.Instant;
import java.util.Map;

import com.gdghost.common.audit.OpsAction;

public record OpsAuditLogResponse(long id, OpsAction action, Actor actor, Map<String, Object> detail,
                                  Instant createdAt) {

    /** 탈퇴한 운영진이면 nickname이 null */
    public record Actor(long id, String nickname, String staffTitle) {
    }

}
