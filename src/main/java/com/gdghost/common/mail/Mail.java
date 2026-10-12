package com.gdghost.common.mail;

/**
 * 짧은 텍스트 메일. 용도는 가입 인증 · 비밀번호 재설정 · 아이디 찾기 · 이메일 변경뿐.
 */
public record Mail(String to, String subject, String text) {
}
