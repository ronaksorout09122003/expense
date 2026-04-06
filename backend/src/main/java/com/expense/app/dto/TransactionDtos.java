package com.expense.app.dto;

import com.expense.app.enums.TransactionStatus;
import com.expense.app.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransactionRequest(
        @NotNull TransactionType transactionType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull OffsetDateTime transactionAt,
        UUID fromAccountId,
        UUID toAccountId,
        UUID categoryId,
        UUID counterpartyId,
        @Size(max = 500) String note,
        LocalDate dueDate,
        @Size(max = 100) String referenceNo,
        @Size(max = 180) String locationText,
        UUID baseTxnId,
        boolean shared,
        Integer sharedParticipantCount,
        UUID householdId
    ) {
    }

    public record SettlementInfoResponse(
        UUID id,
        UUID settlementTxnId,
        OffsetDateTime transactionAt,
        BigDecimal settledAmount,
        String note
    ) {
    }

    public record TransactionSummaryResponse(
        UUID id,
        TransactionType transactionType,
        BigDecimal amount,
        OffsetDateTime transactionAt,
        String note,
        TransactionStatus status,
        UUID fromAccountId,
        String fromAccountName,
        UUID toAccountId,
        String toAccountName,
        UUID categoryId,
        String categoryName,
        String categoryPath,
        UUID counterpartyId,
        String counterpartyName,
        LocalDate dueDate,
        BigDecimal outstandingAmount,
        boolean shared,
        Integer sharedParticipantCount,
        UUID householdId,
        String householdName
    ) {
    }

    public record TransactionDetailResponse(
        UUID id,
        TransactionType transactionType,
        BigDecimal amount,
        OffsetDateTime transactionAt,
        UUID fromAccountId,
        String fromAccountName,
        UUID toAccountId,
        String toAccountName,
        UUID categoryId,
        String categoryName,
        String categoryPath,
        UUID counterpartyId,
        String counterpartyName,
        String note,
        String referenceNo,
        String locationText,
        LocalDate dueDate,
        TransactionStatus status,
        BigDecimal outstandingAmount,
        boolean shared,
        Integer sharedParticipantCount,
        UUID householdId,
        String householdName,
        List<SettlementInfoResponse> settlements
    ) {
    }
}
