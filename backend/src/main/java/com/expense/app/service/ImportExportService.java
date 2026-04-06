package com.expense.app.service;

import com.expense.app.dto.ImportExportDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportExportService {

    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    public String exportTransactionsCsv(UUID userId) {
        List<TransactionEntity> transactions = transactionService.findAllVisibleTransactions(userId);
        StringBuilder builder = new StringBuilder();
        builder.append("transactionType,amount,transactionAt,fromAccountId,toAccountId,categoryId,counterpartyId,note,dueDate,referenceNo,locationText,shared,sharedParticipantCount,householdId\n");
        for (TransactionEntity transaction : transactions) {
            builder.append(transaction.getTransactionType()).append(',')
                .append(transaction.getAmount()).append(',')
                .append(transaction.getTransactionAt()).append(',')
                .append(value(transaction.getFromAccount() != null ? transaction.getFromAccount().getId() : null)).append(',')
                .append(value(transaction.getToAccount() != null ? transaction.getToAccount().getId() : null)).append(',')
                .append(value(transaction.getCategory() != null ? transaction.getCategory().getId() : null)).append(',')
                .append(value(transaction.getCounterparty() != null ? transaction.getCounterparty().getId() : null)).append(',')
                .append(csv(value(transaction.getNote()))).append(',')
                .append(value(transaction.getDueDate())).append(',')
                .append(csv(value(transaction.getReferenceNo()))).append(',')
                .append(csv(value(transaction.getLocationText()))).append(',')
                .append(transaction.isShared()).append(',')
                .append(transaction.getSharedParticipantCount()).append(',')
                .append(value(transaction.getHousehold() != null ? transaction.getHousehold().getId() : null))
                .append('\n');
        }
        return builder.toString();
    }

    @Transactional
    public ImportExportDtos.TransactionImportResponse importTransactions(UUID userId, ImportExportDtos.TransactionImportRequest request) {
        List<String> lines = Arrays.stream(request.csvContent().split("\\r?\\n"))
            .filter(line -> !line.isBlank())
            .toList();
        if (lines.isEmpty()) {
            return new ImportExportDtos.TransactionImportResponse(0, List.of());
        }
        List<String> headers = parseCsvLine(lines.get(0)).stream().map(String::trim).toList();
        Map<String, Integer> index = java.util.stream.IntStream.range(0, headers.size())
            .boxed()
            .collect(Collectors.toMap(headers::get, Function.identity()));

        List<ImportExportDtos.ImportPreviewRow> preview = new ArrayList<>();
        int importedCount = 0;
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            String[] parts = parseCsvLine(line).toArray(String[]::new);
            try {
                TransactionDtos.TransactionRequest transactionRequest = parseRow(parts, index);
                preview.add(new ImportExportDtos.ImportPreviewRow(lineIndex + 1, true, request.dryRun() ? "Ready to import" : "Imported"));
                if (!request.dryRun()) {
                    transactionService.create(userId, transactionRequest);
                    importedCount++;
                }
            } catch (Exception exception) {
                preview.add(new ImportExportDtos.ImportPreviewRow(lineIndex + 1, false, exception.getMessage()));
            }
        }
        return new ImportExportDtos.TransactionImportResponse(importedCount, preview);
    }

    private TransactionDtos.TransactionRequest parseRow(String[] parts, Map<String, Integer> index) {
        return new TransactionDtos.TransactionRequest(
            TransactionType.valueOf(read(parts, index, "transactionType").toUpperCase(Locale.ROOT)),
            new BigDecimal(read(parts, index, "amount")),
            OffsetDateTime.parse(read(parts, index, "transactionAt")),
            uuid(readOptional(parts, index, "fromAccountId")),
            uuid(readOptional(parts, index, "toAccountId")),
            uuid(readOptional(parts, index, "categoryId")),
            uuid(readOptional(parts, index, "counterpartyId")),
            blankToNull(readOptional(parts, index, "note")),
            localDate(readOptional(parts, index, "dueDate")),
            blankToNull(readOptional(parts, index, "referenceNo")),
            blankToNull(readOptional(parts, index, "locationText")),
            null,
            Boolean.parseBoolean(readOptional(parts, index, "shared")),
            integer(readOptional(parts, index, "sharedParticipantCount"), 1),
            uuid(readOptional(parts, index, "householdId"))
        );
    }

    private String read(String[] parts, Map<String, Integer> index, String key) {
        Integer position = index.get(key);
        if (position == null || position >= parts.length || parts[position].isBlank()) {
            throw new IllegalArgumentException("Missing required column: " + key);
        }
        return parts[position].trim();
    }

    private String readOptional(String[] parts, Map<String, Integer> index, String key) {
        Integer position = index.get(key);
        if (position == null || position >= parts.length) {
            return null;
        }
        String value = parts[position].trim();
        return value.isBlank() ? null : value;
    }

    private UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private LocalDate localDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private Integer integer(String value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String csv(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString());
        return values;
    }
}
