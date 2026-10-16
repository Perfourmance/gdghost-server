package com.gdghost.auth;

import java.util.EnumSet;
import java.util.Set;

public enum VerificationPurpose {

    SIGNUP("가입 인증번호"),
    RESET("비밀번호 재설정 인증번호"),
    EMAIL_CHANGE("이메일 변경 인증번호"),
    /** 번호 없는 안내 메일. 발송 제한만 재설정과 같이 센다 */
    FIND_LOGIN_ID("아이디 안내");

    private final String mailSubject;

    VerificationPurpose(String mailSubject) {
        this.mailSubject = mailSubject;
    }

    public String mailSubject() {
        return mailSubject;
    }

    /** 재발송 대기 · 하루 횟수를 같이 세는 용도들 */
    public Set<VerificationPurpose> limitGroup() {
        return switch (this) {
            case RESET, FIND_LOGIN_ID -> EnumSet.of(RESET, FIND_LOGIN_ID);
            default -> EnumSet.of(this);
        };
    }

}
