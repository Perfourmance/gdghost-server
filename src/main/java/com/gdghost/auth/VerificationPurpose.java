package com.gdghost.auth;

public enum VerificationPurpose {

    SIGNUP("가입 인증번호"),
    RESET("비밀번호 재설정 인증번호"),
    EMAIL_CHANGE("이메일 변경 인증번호");

    private final String mailSubject;

    VerificationPurpose(String mailSubject) {
        this.mailSubject = mailSubject;
    }

    public String mailSubject() {
        return mailSubject;
    }

}
