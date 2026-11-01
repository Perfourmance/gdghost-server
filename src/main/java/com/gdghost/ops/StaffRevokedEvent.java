package com.gdghost.ops;

/** 해제하면 직함이 비워지므로 이전 직함을 담는다 */
public record StaffRevokedEvent(long actorId, long memberId, String staffTitle) {
}
