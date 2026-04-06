package com.expense.app.service;

import com.expense.app.dto.LedgerDtos;
import com.expense.app.dto.TransactionDtos;
import com.expense.app.entity.Counterparty;
import com.expense.app.entity.TransactionEntity;
import com.expense.app.enums.TransactionType;
import com.expense.app.exception.ApiException;
import com.expense.app.exception.NotFoundException;
import com.expense.app.repository.CounterpartyRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionService transactionService;
    private final CounterpartyRepository counterpartyRepository;
    private final FinanceMathService financeMathService;

    @Transactional(readOnly = true)
    public List<LedgerDtos.LedgerSummaryResponse> receivable(UUID userId) {
        return summarize(userId, TransactionType.LEND);
    }

    @Transactional(readOnly = true)
    public List<LedgerDtos.LedgerSummaryResponse> payable(UUID userId) {
        return summarize(userId, TransactionType.BORROW);
    }

    @Transactional(readOnly = true)
    public LedgerDtos.CounterpartyLedgerResponse counterpartyLedger(UUID userId, UUID counterpartyId) {
        Counterparty counterparty = counterpartyRepository.findByIdAndUserIdAndDeletedAtIsNull(counterpartyId, userId)
            .orElseThrow(() -> new NotFoundException("Counterparty not found"));
        List<TransactionEntity> transactions = transactionService.findAllVisibleTransactions(userId).stream()
            .filter(transaction -> transaction.getCounterparty() != null && transaction.getCounterparty().getId().equals(counterpartyId))
            .sorted(Comparator.comparing(TransactionEntity::getTransactionAt).reversed())
            .toList();
        Map<UUID, BigDecimal> outstanding = financeMathService.calculateOutstandingByBaseTxn(
            transactionService.findAllVisibleTransactions(userId),
            transactionService.findSettlements(userId)
        );
        BigDecimal receivableOutstanding = transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.LEND)
            .map(transaction -> outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payableOutstanding = transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == TransactionType.BORROW)
            .map(transaction -> outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TransactionDtos.TransactionDetailResponse> items = transactions.stream()
            .map(transaction -> transactionService.get(userId, transaction.getId()))
            .toList();

        return new LedgerDtos.CounterpartyLedgerResponse(
            counterparty.getId(),
            counterparty.getName(),
            receivableOutstanding,
            payableOutstanding,
            items
        );
    }

    @Transactional
    public TransactionDtos.TransactionDetailResponse createSettlement(UUID userId, LedgerDtos.SettlementRequest request) {
        if (request.baseTxnId() == null || request.accountId() == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Base transaction, account, and amount are required");
        }

        TransactionEntity baseTxn = transactionService.getOwnedTransaction(userId, request.baseTxnId());
        TransactionType transactionType;
        UUID fromAccountId = null;
        UUID toAccountId = null;
        if (baseTxn.getTransactionType() == TransactionType.LEND) {
            transactionType = TransactionType.REPAYMENT_IN;
            toAccountId = request.accountId();
        } else if (baseTxn.getTransactionType() == TransactionType.BORROW) {
            transactionType = TransactionType.REPAYMENT_OUT;
            fromAccountId = request.accountId();
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Settlement can only be created for lend or borrow transactions");
        }

        return transactionService.create(
            userId,
            new TransactionDtos.TransactionRequest(
                transactionType,
                request.amount(),
                request.transactionAt() != null ? request.transactionAt() : OffsetDateTime.now(),
                fromAccountId,
                toAccountId,
                null,
                baseTxn.getCounterparty() != null ? baseTxn.getCounterparty().getId() : null,
                request.note(),
                null,
                null,
                null,
                baseTxn.getId(),
                false,
                1,
                null
            )
        );
    }

    private List<LedgerDtos.LedgerSummaryResponse> summarize(UUID userId, TransactionType baseType) {
        List<TransactionEntity> transactions = transactionService.findAllVisibleTransactions(userId);
        Map<UUID, BigDecimal> outstanding = financeMathService.calculateOutstandingByBaseTxn(transactions, transactionService.findSettlements(userId));

        return transactions.stream()
            .filter(transaction -> transaction.getTransactionType() == baseType)
            .filter(transaction -> transaction.getCounterparty() != null)
            .collect(java.util.stream.Collectors.groupingBy(transaction -> transaction.getCounterparty().getId()))
            .values()
            .stream()
            .map(group -> {
                Counterparty counterparty = group.getFirst().getCounterparty();
                BigDecimal totalBaseAmount = group.stream()
                    .map(TransactionEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal outstandingAmount = group.stream()
                    .map(transaction -> outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                long openItems = group.stream().filter(transaction -> outstanding.getOrDefault(transaction.getId(), BigDecimal.ZERO).signum() > 0).count();
                OffsetDateTime lastActivityAt = group.stream().map(TransactionEntity::getTransactionAt).max(OffsetDateTime::compareTo).orElse(null);
                return new LedgerDtos.LedgerSummaryResponse(
                    counterparty.getId(),
                    counterparty.getName(),
                    outstandingAmount,
                    totalBaseAmount,
                    (int) openItems,
                    lastActivityAt
                );
            })
            .filter(summary -> summary.outstandingAmount().signum() > 0)
            .sorted(Comparator.comparing(LedgerDtos.LedgerSummaryResponse::outstandingAmount).reversed())
            .toList();
    }
}
