package com.gdghost.embedding;

/** 메시지에는 상태 코드 · 예외 종류만 담는다 (주소에 계정 ID가 있다) */
public class EmbeddingException extends RuntimeException {

    private final boolean badInput;

    public EmbeddingException(String message, boolean badInput) {
        super(message);
        this.badInput = badInput;
    }

    /** 이 글만의 문제 — 다시 불러도 같고, 다른 글은 될 수 있다 */
    public boolean isBadInput() {
        return badInput;
    }

}
