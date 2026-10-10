package com.gdghost.common.error;

/** 에러 응답 errors[]의 한 칸 — 어느 입력칸이 왜 틀렸는지 */
public record ErrorField(String field, String message) {
}
