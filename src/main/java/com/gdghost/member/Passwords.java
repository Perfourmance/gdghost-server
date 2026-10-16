package com.gdghost.member;

import java.util.regex.Pattern;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;

/** 가입 · 비밀번호 재설정 · 비밀번호 변경이 같이 쓰는 규칙 */
public final class Passwords {

    // 8–64자(BCrypt 72바이트 한계 안), 영문 · 숫자 · 특수문자 모두, 공백 없음
    private static final Pattern RULE = Pattern.compile("(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,64}");

    private Passwords() {
    }

    /** 어긋나면 400 PASSWORD_POLICY */
    public static void check(String password) {
        if (!RULE.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY, "비밀번호는 8자 이상, 영문 · 숫자 · 특수문자를 모두 넣어 주세요.");
        }
    }

}
