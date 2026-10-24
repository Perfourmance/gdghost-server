package com.gdghost.question;

/** 익명 표기. 번호는 question_anon_alias 기준(글 단위) — 0이 글쓴이 */
final class AnonLabels {

    static final String QUESTION_AUTHOR = "익명";

    private AnonLabels() {
    }

    static String answer(Integer aliasNo) {
        if (aliasNo == null) {
            return QUESTION_AUTHOR;
        }
        return aliasNo == 0 ? "익명(글쓴이)" : "익명" + aliasNo;
    }

}
