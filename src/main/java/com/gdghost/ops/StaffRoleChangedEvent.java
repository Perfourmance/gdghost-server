package com.gdghost.ops;

/** 운영진 지정 · 직함 변경 */
public record StaffRoleChangedEvent(long actorId, long memberId) {
}
