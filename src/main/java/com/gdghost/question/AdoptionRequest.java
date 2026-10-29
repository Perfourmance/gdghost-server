package com.gdghost.question;

import jakarta.validation.constraints.NotNull;

public record AdoptionRequest(@NotNull Long answerId) {
}
