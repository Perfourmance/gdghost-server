package com.gdghost.common.audit;

/**
 * 운영진 · 개발자의 민감한 행동 (ERD ops_audit_log.action).
 */
public enum OpsAction {
    EMAIL_VIEW,
    EMAIL_CHANGE_REQUEST,
    SUSPEND,
    UNSUSPEND,
    STAFF_ASSIGN,
    STAFF_REVOKE,
    INVITE_CODE_ISSUE,
    INVITE_CODE_REVOKE,
    REPORT_HIDE,
    REPORT_RESTORE,
    REPORT_DISMISS,
    CERT_REVIEW,
    GRADUATE,
    ROUTING_EXPORT
}
