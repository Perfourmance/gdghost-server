package com.gdghost.ops;

/** 운영진 지정. 해제는 10단계에서 */
public record StaffRoleChangedEvent(long actorId, long memberId) {
}
