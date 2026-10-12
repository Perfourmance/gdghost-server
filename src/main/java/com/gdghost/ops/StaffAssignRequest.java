package com.gdghost.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffAssignRequest(
        @NotBlank @Size(min = 2, max = 10) String staffTitle) {
}
