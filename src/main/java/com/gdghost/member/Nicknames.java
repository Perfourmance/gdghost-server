package com.gdghost.member;

/**
 * 닉네임 규칙. 익명 질문 · 답변의 표시 이름과 헷갈리지 않게 '익명'으로 시작하는 닉네임은 막는다.
 */
public final class Nicknames {

    private static final String RESERVED_PREFIX = "익명";

    private Nicknames() {
    }

    public static boolean isReserved(String nickname) {
        return nickname.startsWith(RESERVED_PREFIX);
    }

}
