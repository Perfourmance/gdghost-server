package com.gdghost.notification;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ids와 all(true) 중 하나만 */
public record NotificationReadRequest(
        @Size(max = 100) List<@NotNull Long> ids,
        Boolean all) {

    boolean readingAll() {
        return Boolean.TRUE.equals(all);
    }

    @AssertTrue(message = "ids와 all 중 하나만 보내야 합니다.")
    boolean isOneOf() {
        return readingAll() == (ids == null || ids.isEmpty());
    }

}
