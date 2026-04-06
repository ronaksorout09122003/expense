package com.expense.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEntryResponse(
        UUID id,
        String entityName,
        UUID entityId,
        String actionName,
        String oldValue,
        String newValue,
        OffsetDateTime createdAt
    ) {
    }
}
