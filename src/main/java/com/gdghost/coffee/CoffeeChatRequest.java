package com.gdghost.coffee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoffeeChatRequest(
        @NotNull Long receiverId,
        @NotBlank @Size(min = 10, max = 200) String purpose) {
}
