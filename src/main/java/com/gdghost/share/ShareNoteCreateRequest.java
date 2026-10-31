package com.gdghost.share;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 떼고 검사한다 */
public record ShareNoteCreateRequest(@NotNull @Size(min = 1, max = 100) String body) {

    public ShareNoteCreateRequest {
        body = body == null ? null : body.strip();
    }

}
