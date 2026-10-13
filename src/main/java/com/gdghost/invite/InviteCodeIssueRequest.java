package com.gdghost.invite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InviteCodeIssueRequest(
        @NotNull @Min(1) @Max(99) Integer generation,
        @Min(1) @Max(90) Integer expiresInDays,
        @NotNull @Min(1) @Max(200) Integer maxUses) {

    static final int DEFAULT_EXPIRES_IN_DAYS = 30;

    public int expiresInDaysOrDefault() {
        return expiresInDays == null ? DEFAULT_EXPIRES_IN_DAYS : expiresInDays;
    }

}
