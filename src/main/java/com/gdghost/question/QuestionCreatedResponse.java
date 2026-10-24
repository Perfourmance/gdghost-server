package com.gdghost.question;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** 질문 상세와 같은 모양 + directDelivery */
public record QuestionCreatedResponse(@JsonUnwrapped QuestionDetail question, DirectDelivery directDelivery) {
}
