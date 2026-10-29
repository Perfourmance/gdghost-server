package com.gdghost.report;

import jakarta.validation.constraints.NotNull;

public record ReportHandleRequest(@NotNull ReportAction action) {
}
