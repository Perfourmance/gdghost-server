package com.gdghost.auth;

/** loginToken은 쿠키에 넣을 원문 */
public record LoginResult(String loginToken, boolean remember, MeResponse me) {
}
