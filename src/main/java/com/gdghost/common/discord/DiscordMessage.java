package com.gdghost.common.discord;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹훅 embed 하나. 사용자가 쓴 글은 description에만, {@link #escape}를 거쳐 넣는다.
 *
 * @param kind        색 · 썸네일 · 배치
 * @param title       분류 (예: 새 질문 · 취업·채용)
 * @param url         눌렀을 때 열 서비스 주소, 없으면 null
 * @param description 본문 줄, 없으면 null
 * @param fields      표처럼 나눠 보일 칸 (누가 · 누구 · 사유 등)
 */
public record DiscordMessage(DiscordKind kind, String title, String url, String description, List<Field> fields) {

    /** inline이면 옆 칸과 한 줄에 */
    public record Field(String name, String value, boolean inline) {

        public static Field inline(String name, String value) {
            return new Field(name, value, true);
        }

        public static Field wide(String name, String value) {
            return new Field(name, value, false);
        }

    }

    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~`|>\\[\\]()#\\-<]");
    private static final int MAX_DESCRIPTION = 4000;

    public DiscordMessage {
        if (description != null && description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION) + "…";
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public DiscordMessage(DiscordKind kind, String title, String url, String description) {
        this(kind, title, url, description, List.of());
    }

    /** 이름이 같은 칸의 값, 없으면 null */
    public String field(String name) {
        return fields.stream().filter(field -> field.name().equals(name)).map(Field::value).findFirst().orElse(null);
    }

    /** 마크다운 링크. 글자는 escape한 것을 넘긴다 */
    public static String link(String escapedText, String url) {
        return "[" + escapedText + "](" + url + ")";
    }

    /** 사용자 글의 마크다운 기호를 글자 그대로 보이게 */
    public static String escape(String text) {
        return text == null ? "" : MARKDOWN.matcher(text).replaceAll(m -> Matcher.quoteReplacement("\\" + m.group()));
    }

}
