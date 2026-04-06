package com.expense.app.controller;

import com.expense.app.dto.ImportExportDtos;
import com.expense.app.security.UserPrincipal;
import com.expense.app.service.ImportExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data")
@RequiredArgsConstructor
public class ImportExportController {

    private final ImportExportService importExportService;

    @GetMapping("/export/transactions")
    public ResponseEntity<String> exportTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
            .contentType(MediaType.TEXT_PLAIN)
            .body(importExportService.exportTransactionsCsv(principal.getUserId()));
    }

    @PostMapping("/import/transactions")
    public ImportExportDtos.TransactionImportResponse importTransactions(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody ImportExportDtos.TransactionImportRequest request
    ) {
        return importExportService.importTransactions(principal.getUserId(), request);
    }
}
