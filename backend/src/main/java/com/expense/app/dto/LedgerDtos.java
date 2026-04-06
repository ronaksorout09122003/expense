package com.expense.app.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record SettlementRequest(
        UUID baseTxnId,
        UUID accountId,
        BigDecimal amount,
        OffsetDateTime transactionAt,
        String note
    ) {
    }

    public record LedgerSummaryResponse(
        UUID counterpartyId,
        String counterpartyName,
        BigDecimal outstandingAmount,
        BigDecimal totalBaseAmount,
        int openItems,
        OffsetDateTime lastActivityAt
    ) {
    }

    public record CounterpartyLedgerResponse(
        UUID counterpartyId,
        String counterpartyName,
        BigDecimal receivableOutstanding,
        BigDecimal payableOutstanding,
        List<TransactionDtos.TransactionDetailResponse> transactions
    ) {
    }
}
