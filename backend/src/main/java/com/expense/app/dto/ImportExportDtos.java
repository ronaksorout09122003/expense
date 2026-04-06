package com.expense.app.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class ImportExportDtos {

    private ImportExportDtos() {
    }

    public record TransactionImportRequest(
        @NotBlank String csvContent,
        boolean dryRun
    ) {
    }

    public record ImportPreviewRow(
        int lineNumber,
        boolean valid,
        String message
    ) {
    }

    public record TransactionImportResponse(
        int importedCount,
        List<ImportPreviewRow> preview
    ) {
    }
}
