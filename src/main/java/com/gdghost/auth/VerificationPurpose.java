package com.gdghost.auth;

import java.util.EnumSet;
import java.util.Set;

public enum VerificationPurpose {

    SIGNUP("가입 인증번호"),
    RESET("비밀번호 재설정 인증번호"),
    EMAIL_CHANGE("이메일 변경 인증번호"),
    /** 운영진 요청 이메일 변경의 확인 링크. 6자리 번호와 섞이지 않게 용도를 나눈다 */
    EMAIL_CHANGE_LINK("이메일 변경 확인 링크"),
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
            case EMAIL_CHANGE, EMAIL_CHANGE_LINK -> EnumSet.of(EMAIL_CHANGE, EMAIL_CHANGE_LINK);
            default -> EnumSet.of(this);
        };
    }

}
