package com.gdghost.ops;

import java.util.List;

public record GraduationResponse(int updatedCount, List<Long> skippedIds) {
}
