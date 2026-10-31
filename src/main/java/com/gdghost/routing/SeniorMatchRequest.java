package com.gdghost.routing;

import com.gdghost.question.QuestionCategory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다 */
public record SeniorMatchRequest(
        @NotNull QuestionCategory category,
        @NotNull Long interestTagId,
        @NotNull @Size(min = 10, max = 120) String text) {

    public SeniorMatchRequest {
        text = text == null ? null : text.strip();
    }

}
