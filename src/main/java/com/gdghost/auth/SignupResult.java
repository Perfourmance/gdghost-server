package com.gdghost.auth;

/** loginToken은 쿠키에 넣을 원문 */
public record SignupResult(String loginToken, MeResponse me) {
}
