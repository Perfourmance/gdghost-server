package com.gdghost.question;

/** 분류 · 제목 · 본문이 실제로 바뀌었을 때 */
public record QuestionEditedEvent(long questionId) {
}
