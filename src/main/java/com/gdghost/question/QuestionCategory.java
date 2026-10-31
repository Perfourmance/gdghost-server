package com.gdghost.question;

public enum QuestionCategory {
    JOB("취업·채용"), CAREER("커리어·이직"), PATH("진로 고민"), CAMPUS("학교생활"), STUDY("기술·공부");

    private final String label;

    QuestionCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
