package com.gdghost.common.discord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹훅 embed 하나. 사용자가 쓴 글은 description에만, {@link #escape}를 거쳐 넣는다.
 *
 * @param title       분류 (예: 새 질문 · 취업·채용)
 * @param url         눌렀을 때 열 서비스 주소, 없으면 null
 * @param description 본문 줄, 없으면 null
 */
public record DiscordMessage(String title, String url, String description) {

    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~`|>\\[\\]()#\\-<]");
    private static final int MAX_DESCRIPTION = 4000;

    public DiscordMessage {
        if (description != null && description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION) + "…";
        }
    }

    /** 사용자 글의 마크다운 기호를 글자 그대로 보이게 */
    public static String escape(String text) {
        return text == null ? "" : MARKDOWN.matcher(text).replaceAll(m -> Matcher.quoteReplacement("\\" + m.group()));
    }

}
