package com.gdghost.admin;

import java.time.LocalDate;

public record RoutingExportEvent(long actorId, LocalDate from, LocalDate to, int rows) {
}
