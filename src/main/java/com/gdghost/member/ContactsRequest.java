package com.gdghost.member;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 통째로 교체 — 빠진 종류는 지운다. 종류 중복 · 전화번호 형식은 서비스에서 검사 */
public record ContactsRequest(@NotNull @Size(max = 4) List<@NotNull @Valid Item> contacts) {

    /** isPublic을 빼면 비공개 */
    public record Item(@NotNull ContactType type, @NotBlank @Size(max = 100) String value, Boolean isPublic) {

        boolean publicOrDefault() {
            return Boolean.TRUE.equals(isPublic);
        }

    }

}
