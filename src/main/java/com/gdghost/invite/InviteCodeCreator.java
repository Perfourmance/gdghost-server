package com.gdghost.invite;

/** 목록에서 코드와 발급한 운영진 닉네임을 한 쿼리로 읽는다 */
public record InviteCodeCreator(InviteCode code, String creatorNickname) {
}
