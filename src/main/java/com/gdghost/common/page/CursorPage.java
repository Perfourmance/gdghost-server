package com.gdghost.common.page;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import com.gdghost.common.error.BusinessException;
import com.gdghost.common.error.ErrorCode;
import com.gdghost.common.error.ErrorField;

/**
 * 일반 목록 응답. cursor는 서버가 준 문자열을 그대로 돌려받는다 (API 설계서 공통 규칙).
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static final int MAX_SIZE = 50;

    /** size + 1개를 읽어 넘치면 다음 페이지가 있다 */
    public static <T> CursorPage<T> of(List<T> fetched, int size, Function<T, String> cursorOf) {
        if (fetched.size() <= size) {
            return new CursorPage<>(fetched, null, false);
        }
        List<T> items = fetched.subList(0, size);
        return new CursorPage<>(List.copyOf(items), encode(cursorOf.apply(items.getLast())), true);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }

    /** @return 없으면 null. 해석할 수 없으면 400 */
    public static String decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    public static BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, null,
                List.of(new ErrorField("cursor", "처음부터 다시 불러와 주세요.")));
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

}
