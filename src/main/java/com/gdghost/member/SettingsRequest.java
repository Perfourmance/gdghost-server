package com.gdghost.member;

import jakarta.validation.constraints.Size;

/** 보낸 필드만 바꾼다(null은 그대로). coffeeMemo는 빈 문자열이면 지운다 */
public record SettingsRequest(Boolean acceptQuestion, Boolean acceptCoffee, @Size(max = 60) String coffeeMemo,
        Boolean hiddenFromList, Boolean pushOn) {
}
