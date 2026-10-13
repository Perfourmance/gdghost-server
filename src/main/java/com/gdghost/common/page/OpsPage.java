package com.gdghost.common.page;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 운영 · 어드민 목록 응답. page는 0부터.
 */
public record OpsPage<T>(List<T> items, int page, int size, long total) {

    public static <S, T> OpsPage<T> of(Page<S> page, Function<S, T> mapper) {
        return new OpsPage<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

}
