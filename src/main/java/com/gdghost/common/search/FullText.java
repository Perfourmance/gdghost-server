package com.gdghost.common.search;

/** MATCH … AGAINST (? IN BOOLEAN MODE)에 넘길 검색어 */
public final class FullText {

    private FullText() {
    }

    /** 큰따옴표로 묶어 입력한 글자를 그대로 찾는다. 묶지 않으면 + - * ( 같은 연산자가 구문 오류를 낸다 */
    public static String phrase(String query) {
        return '"' + query.replace('"', ' ') + '"';
    }

    /** phrase로 만든 검색어를 LIKE 패턴으로. 태그처럼 색인이 없는 짧은 값을 찾을 때. ESCAPE '!'와 함께 쓴다 */
    public static String like(String phrase) {
        String text = phrase.substring(1, phrase.length() - 1);
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

}
