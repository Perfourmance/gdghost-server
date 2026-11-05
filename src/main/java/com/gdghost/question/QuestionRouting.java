package com.gdghost.question;

import java.time.Instant;
import java.util.List;

/** 질문 상세의 전달 현황. 익명 질문은 reasonsHidden = true, 내 줄이 아니면 to = null · reasons 비움(운영진 단계 제외) */
public record QuestionRouting(List<Delivery> deliveries, boolean reasonsHidden, Mine mine) {

    public record Delivery(String stage, String state, To to, List<String> reasons, Instant sentAt) {
    }

    public record To(String nickname) {
    }

    /** 내가 지목된 경우만, 아니면 null */
    public record Mine(String state, boolean canPass) {
    }

}
