package com.gdghost.common.audit;

/**
 * 부원이 아닌 기록 대상. 부원 대상은 targetMember로 따로 가리킨다.
 */
public enum OpsTargetType {
    INVITE_CODE,
    REPORT,
    CERTIFICATION
}
