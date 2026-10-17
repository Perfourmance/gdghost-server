package com.gdghost.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** reason — 운영진끼리만 본다 */
public record SuspendRequest(@NotBlank @Size(min = 2, max = 200) String reason) {
}
