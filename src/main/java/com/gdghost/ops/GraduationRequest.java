package com.gdghost.ops;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GraduationRequest(
        @NotEmpty @Size(max = 200) List<@NotNull Long> memberIds) {
}
